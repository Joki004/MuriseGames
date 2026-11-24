package com.murisegames.api.modules.Event.Alibi.Game;

import com.murisegames.api.modules.Audit.AuditService;
import com.murisegames.api.modules.Event.Alibi.AlibiCase;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseRepository;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseRules;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.CaseContent;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.Question;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseVersion;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseVersionRepository;
import com.murisegames.api.modules.Event.Alibi.Game.AlibiGameRules.CaseCandidate;
import com.murisegames.api.modules.Event.Alibi.Game.AlibiGameTypes.*;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Referee.RefereeService;
import com.murisegames.api.modules.Event.Scoring.Plan.ScoringPlan;
import com.murisegames.api.modules.Event.Scoring.Plan.ScoringPlanRepository;
import com.murisegames.api.modules.Event.Scoring.Settlement.OfficialResultService;
import com.murisegames.api.modules.Event.Scoring.Settlement.OfficialResultTypes.RecordFixtureResultRequest;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static com.murisegames.api.modules.Event.Alibi.Game.AlibiRound.*;

/**
 * GM-046 / GM-020 / GM-028: playing Unified Alibi on phones.
 *
 * Each team plays in its own fixture slot: pair 1 then pair 2, each with a fresh case.
 * Timing is enforced by the server. Nothing runs in the background: every request
 * that touches a round first moves it forward if a deadline has passed ("tick"),
 * under a row lock, so the state is the same whoever looks at it.
 */
@Service
@RequiredArgsConstructor
public class AlibiGameService {

    public static final String SCORING_HANDLER = "unified_alibi_v1";
    private static final String RESULT_REASON = "Unified Alibi: answers judged in the app.";

    private static final Set<String> STARTED =
            Set.of(PREPARING, QUESTIONING, JUDGING, DONE);

    private final AlibiSessionRepository sessionRepository;
    private final AlibiRoundRepository roundRepository;
    private final AlibiPromptRepository promptRepository;
    private final AlibiAnswerRepository answerRepository;
    private final AlibiCaseRepository caseRepository;
    private final AlibiCaseVersionRepository versionRepository;
    private final ScoringPlanRepository planRepository;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final RefereeService refereeService;
    private final OfficialResultService officialResultService;
    private final UserService userService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    private final SecureRandom random = new SecureRandom();

    // =================================================================================
    // Host (organizers)
    // =================================================================================

    @Transactional
    public HostView host(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);
        tickDueRounds(eventId);
        return buildHostView(eventId);
    }

    @Transactional
    public HostView createSession(Jwt jwt, UUID eventId, CreateSessionRequest request) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);
        lockEvent(eventId);

        PlannedSlot slot = plannedSlots(eventId).stream()
                .filter(s -> s.fixtureId().equals(request.fixtureId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "This fixture is not an Alibi slot in the published scoring plan."));

        if (sessionRepository.findByFixtureId(slot.fixtureId()).isEmpty()) {
            AlibiSession session = new AlibiSession();
            session.setEventId(eventId);
            session.setEventGameId(slot.eventGameId());
            session.setFixtureId(slot.fixtureId());
            session.setTeamId(slot.teamId());
            session.setCreatedBy(userId);
            session = sessionRepository.saveAndFlush(session);

            for (int pair = 1; pair <= 2; pair++) {
                AlibiRound round = new AlibiRound();
                round.setEventId(eventId);
                round.setSessionId(session.getId());
                round.setPairNumber(pair);
                round.setStatus(SETUP);
                round.setQuestionCount(AlibiCaseRules.QUESTION_COUNT);
                roundRepository.save(round);
            }
            roundRepository.flush();
            auditService.record(eventId, userId, "alibi.session_created", "alibi_session",
                    session.getId(), Map.of("teamId", slot.teamId().toString()));
        }
        return buildHostView(eventId);
    }

    /** Choose the pair and the case (or draw one at random) before the round starts. */
    @Transactional
    public HostView setup(Jwt jwt, UUID eventId, UUID roundId, SetupRequest request) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);

        AlibiRound round = lockRound(eventId, roundId);
        if (!SETUP.equals(round.getStatus())) {
            throw conflict("This round has already started; its pair and case are fixed.");
        }
        AlibiSession session = sessionOf(round);

        Map<UUID, String> members = teamMembers(eventId, session.getTeamId());
        if (!members.containsKey(request.playerA()) || !members.containsKey(request.playerB())) {
            throw bad("Both players must be members of this team with an account.");
        }
        if (request.playerA().equals(request.playerB())) {
            throw bad("Choose two different players.");
        }

        List<CaseCandidate> eligible = eligibleCases(eventId, session.getTeamId(), round.getId());
        UUID caseId;
        if (request.random()) {
            if (eligible.isEmpty()) {
                throw conflict("No case is available for this team. Write more cases or ask the "
                        + "team's members not to open them.");
            }
            caseId = eligible.get(random.nextInt(eligible.size())).caseId();
        } else {
            if (request.caseId() == null) {
                throw bad("Choose a case or draw one at random.");
            }
            boolean allowed = eligible.stream().anyMatch(c -> c.caseId().equals(request.caseId()));
            if (!allowed) {
                throw conflict("This case cannot be given to this team: it is not ready, was "
                        + "already played, or one of the team's members knows it.");
            }
            caseId = request.caseId();
        }

        round.setPlayerA(request.playerA());
        round.setPlayerB(request.playerB());
        round.setCaseId(caseId);
        roundRepository.saveAndFlush(round);
        return buildHostView(eventId);
    }

    /** Pins the exact case version and opens the 90-second preparation. */
    @Transactional
    public HostView start(Jwt jwt, UUID eventId, UUID roundId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);

        AlibiRound round = lockRound(eventId, roundId);
        if (!SETUP.equals(round.getStatus())) {
            throw conflict("This round has already started.");
        }
        if (round.getPlayerA() == null || round.getPlayerB() == null || round.getCaseId() == null) {
            throw conflict("Choose the pair and the case first.");
        }
        AlibiSession session = sessionOf(round);
        for (AlibiRound other : roundRepository.findBySessionIdOrderByPairNumberAsc(session.getId())) {
            if (!other.getId().equals(round.getId())
                    && (PREPARING.equals(other.getStatus()) || QUESTIONING.equals(other.getStatus()))) {
                throw conflict("Finish the other pair's questions before starting this round.");
            }
        }

        AlibiCase alibiCase = caseRepository.findByIdAndEventIdAndArchivedAtIsNull(round.getCaseId(), eventId)
                .orElseThrow(() -> conflict("The chosen case was removed from the pool; choose another."));
        AlibiCaseVersion version = versionRepository
                .findByCaseIdAndVersionNumber(alibiCase.getId(), alibiCase.getCurrentVersion())
                .orElseThrow(() -> conflict("The chosen case has no saved version."));
        CaseContent content = AlibiCaseRules.fromMap(version.getContent());
        if (!AlibiCaseRules.isReady(content)) {
            throw conflict("The chosen case is not ready to play: " + AlibiCaseRules.missing(content));
        }

        Instant now = Instant.now();
        round.setCaseVersionId(version.getId());
        round.setQuestionCount(content.questions().size());
        round.setStatus(PREPARING);
        round.setStartedAt(now);
        round.setStartedBy(userId);
        round.setPrepEndsAt(now.plus(AlibiGameRules.PREPARATION));
        roundRepository.saveAndFlush(round);

        auditService.record(eventId, userId, "alibi.round_started", "alibi_round", round.getId(),
                Map.of("pair", round.getPairNumber(), "caseVersionId", version.getId().toString()));
        return buildHostView(eventId);
    }

    /** The partners have separated: question 1 opens on both phones. */
    @Transactional
    public HostView startQuestions(Jwt jwt, UUID eventId, UUID roundId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);

        AlibiRound round = lockRound(eventId, roundId);
        if (!PREPARING.equals(round.getStatus())) {
            throw conflict("Questions can only start after the preparation.");
        }
        openQuestion(round, 1, Instant.now(), contentOf(round));
        round.setStatus(QUESTIONING);
        roundRepository.saveAndFlush(round);
        return buildHostView(eventId);
    }

    /** Closes the current question now; unanswered sides are recorded as missing. */
    @Transactional
    public HostView skipQuestion(Jwt jwt, UUID eventId, UUID roundId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);

        AlibiRound round = lockRound(eventId, roundId);
        if (!QUESTIONING.equals(round.getStatus())) {
            throw conflict("No question is open in this round.");
        }
        AlibiPrompt prompt = currentPrompt(round);
        Instant now = Instant.now();
        prompt.setClosesAt(now.minus(AlibiGameRules.GRACE));
        promptRepository.saveAndFlush(prompt);
        tick(round, now);

        auditService.record(eventId, userId, "alibi.question_closed_early", "alibi_prompt",
                prompt.getId(), Map.of("question", prompt.getQuestionNumber()));
        return buildHostView(eventId);
    }

    /** Shows the next judged question with both answers on the TV. */
    @Transactional
    public HostView revealNext(Jwt jwt, UUID eventId, UUID roundId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);

        AlibiRound round = lockRound(eventId, roundId);
        tick(round, Instant.now());
        if (!JUDGING.equals(round.getStatus())) {
            throw conflict(DONE.equals(round.getStatus())
                    ? "Every question of this round is already revealed."
                    : "Wait until every question has been answered.");
        }
        int next = round.getRevealedCount() + 1;
        AlibiPrompt prompt = promptRepository.findByRoundIdAndQuestionNumber(round.getId(), next)
                .orElseThrow(() -> conflict("Question " + next + " is missing."));
        if (prompt.getVerdict() == null) {
            throw conflict("Question " + next + " has not been judged yet.");
        }
        prompt.setRevealedAt(Instant.now());
        promptRepository.saveAndFlush(prompt);

        round.setRevealedCount(next);
        if (next >= round.getQuestionCount()) {
            round.setStatus(DONE);
        }
        roundRepository.saveAndFlush(round);
        return buildHostView(eventId);
    }

    /** Sends both pairs' matches to the team's fixture: raw /20, normalized /5. */
    @Transactional
    public HostView record(Jwt jwt, UUID eventId, UUID sessionId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, userId);

        AlibiSession session = sessionRepository.lock(eventId, sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Alibi session not found."));
        List<AlibiRound> rounds = roundRepository.findBySessionIdOrderByPairNumberAsc(session.getId());
        if (rounds.size() != 2 || rounds.stream().anyMatch(r -> !DONE.equals(r.getStatus()))) {
            throw conflict("Both pairs must have played and every question must be revealed.");
        }

        List<Integer> values = new ArrayList<>();
        for (AlibiRound round : rounds) {
            values.add(matchesOf(round.getId()));
        }
        officialResultService.recordFixtureResult(
                jwt, eventId, session.getFixtureId(),
                new RecordFixtureResultRequest(Map.of(session.getTeamId(), values), RESULT_REASON)
        );

        session.setRecordedAt(Instant.now());
        session.setRecordedBy(userId);
        sessionRepository.saveAndFlush(session);
        return buildHostView(eventId);
    }

    // =================================================================================
    // Judge (organizers and assigned referees; never for their own team)
    // =================================================================================

    @Transactional
    public JudgeView judge(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        Set<String> roles = membershipService.requireAccess(eventId, userId);
        if (!roles.contains(RefereeService.ROLE_ORGANIZER) && !roles.contains(RefereeService.ROLE_REFEREE)) {
            throw new AccessDeniedException("Organizer or referee permission required.");
        }
        tickDueRounds(eventId);

        Map<UUID, AlibiSession> sessions = sessionsById(eventId);
        Map<UUID, String[]> teams = teamInfo(eventId);
        List<JudgeRound> out = new ArrayList<>();

        for (AlibiRound round : startedRounds(eventId)) {
            AlibiSession session = sessions.get(round.getSessionId());
            if (session == null || !mayJudge(eventId, userId, session)) {
                continue;
            }
            CaseContent content = contentOf(round);
            List<AlibiPrompt> prompts = promptRepository.findByRoundIdOrderByQuestionNumberAsc(round.getId());
            Map<UUID, Map<String, AlibiAnswer>> answers = answersByPrompt(prompts);
            Map<UUID, String> names = displayNames(eventId, List.of(round.getPlayerA(), round.getPlayerB()));

            List<JudgePrompt> judged = new ArrayList<>();
            for (AlibiPrompt prompt : prompts) {
                if (prompt.getCompareReadyAt() == null) {
                    continue; // the other side's answer stays hidden until both are locked
                }
                Question q = questionAt(content, prompt.getQuestionNumber());
                Map<String, AlibiAnswer> pair = answers.getOrDefault(prompt.getId(), Map.of());
                judged.add(new JudgePrompt(
                        prompt.getId(),
                        prompt.getQuestionNumber(),
                        prompt.getQuestionText(),
                        q == null ? List.of() : q.validEquivalents(),
                        q == null ? "" : q.judgeNote(),
                        answerView(pair.get("A")),
                        answerView(pair.get("B")),
                        prompt.getVerdict(),
                        prompt.getVerdictSource(),
                        prompt.getRevealedAt() != null
                ));
            }

            out.add(new JudgeRound(
                    round.getId(),
                    teams.getOrDefault(session.getTeamId(), new String[]{"Team", ""})[0],
                    round.getPairNumber(),
                    round.getStatus(),
                    caseLabel(round.getCaseId()),
                    names.getOrDefault(round.getPlayerA(), "Player A"),
                    names.getOrDefault(round.getPlayerB(), "Player B"),
                    content.brief(),
                    content.fixedFacts(),
                    content.judgeRubric(),
                    round.getQuestionCount(),
                    round.getCurrentQuestion(),
                    judged
            ));
        }
        return new JudgeView(Instant.now(), out);
    }

    @Transactional
    public JudgeView verdict(Jwt jwt, UUID eventId, UUID promptId, VerdictRequest request) {
        UUID userId = userService.extractUserId(jwt);
        Set<String> roles = membershipService.requireAccess(eventId, userId);

        AlibiPrompt found = promptRepository.findByIdAndEventId(promptId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found."));
        AlibiRound round = lockRound(eventId, found.getRoundId());
        AlibiPrompt prompt = promptRepository.lock(eventId, promptId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found."));
        AlibiSession session = sessionOf(round);

        if (!mayJudge(eventId, userId, session)) {
            throw new AccessDeniedException("You cannot judge this round.");
        }
        if (prompt.getCompareReadyAt() == null) {
            throw conflict("Both answers must be locked before judging.");
        }

        boolean match = Boolean.TRUE.equals(request.match());
        Instant now = Instant.now();
        if (prompt.getRevealedAt() != null) {
            // Already on the TV: only an organizer may correct it, with a reason, on the record.
            if (!roles.contains(RefereeService.ROLE_ORGANIZER)) {
                throw new AccessDeniedException("Only an organizer can change a revealed verdict.");
            }
            String reason = request.reason() == null ? "" : request.reason().strip();
            if (reason.length() < 3) {
                throw bad("Give a reason to change a verdict that is already revealed.");
            }
            Boolean previous = prompt.getVerdict();
            prompt.setVerdict(match);
            prompt.setVerdictSource("correction");
            prompt.setDecidedBy(userId);
            prompt.setDecidedAt(now);
            promptRepository.saveAndFlush(prompt);
            auditService.record(eventId, userId, "alibi.verdict_corrected", "alibi_prompt",
                    prompt.getId(), Map.of(
                            "question", prompt.getQuestionNumber(),
                            "from", String.valueOf(previous),
                            "to", String.valueOf(match),
                            "reason", reason));
        } else {
            prompt.setVerdict(match);
            prompt.setVerdictSource("judge");
            prompt.setDecidedBy(userId);
            prompt.setDecidedAt(now);
            promptRepository.saveAndFlush(prompt);
        }
        return judge(jwt, eventId);
    }

    // =================================================================================
    // Player (one side of a pair)
    // =================================================================================

    @Transactional
    public PlayerView me(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, userId);

        AlibiRound round = myRound(eventId, userId);
        if (round == null) {
            return new PlayerView(Instant.now(), null);
        }
        if (QUESTIONING.equals(round.getStatus()) && isDue(round, Instant.now())) {
            round = lockRound(eventId, round.getId());
            tick(round, Instant.now());
        }
        return new PlayerView(Instant.now(), playerRound(eventId, round, userId));
    }

    /**
     * Locks this side's answer. Repeating the request never replaces a locked answer;
     * it just returns the current state.
     */
    @Transactional
    public PlayerView answer(Jwt jwt, UUID eventId, UUID promptId, AnswerRequest request) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, userId);

        AlibiPrompt found = promptRepository.findByIdAndEventId(promptId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Question not found."));
        AlibiRound round = lockRound(eventId, found.getRoundId());
        String side = round.sideOf(userId);
        if (side == null) {
            throw new AccessDeniedException("You are not playing in this pair.");
        }

        Instant now = Instant.now();
        boolean current = QUESTIONING.equals(round.getStatus())
                && found.getQuestionNumber() == round.getCurrentQuestion();
        boolean alreadyLocked = answerRepository.findByPromptIdAndSide(promptId, side).isPresent();

        if (current && !alreadyLocked && now.isBefore(found.getClosesAt().plus(AlibiGameRules.GRACE))) {
            String text = request.text() == null ? "" : request.text().strip();
            if (text.length() > AlibiGameRules.MAX_ANSWER_LENGTH) {
                text = text.substring(0, AlibiGameRules.MAX_ANSWER_LENGTH);
            }
            AlibiAnswer answer = new AlibiAnswer();
            answer.setEventId(eventId);
            answer.setPromptId(promptId);
            answer.setSide(side);
            answer.setUserId(userId);
            answer.setAnswerText(text);
            answer.setMissing(false);
            answer.setAwayMs(Math.max(0, Optional.ofNullable(request.awayMs()).orElse(0)));
            answer.setAwayCount(Math.max(0, Optional.ofNullable(request.awayCount()).orElse(0)));
            answerRepository.saveAndFlush(answer);
        }
        tick(round, now);
        return new PlayerView(Instant.now(), playerRound(eventId, round, userId));
    }

    // =================================================================================
    // TV / audience
    // =================================================================================

    @Transactional
    public PublicView live(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, userId);
        tickDueRounds(eventId);

        AlibiRound round = featuredRound(eventId);
        if (round == null) {
            return new PublicView(Instant.now(), null);
        }
        AlibiSession session = sessionOf(round);
        String[] team = teamInfo(eventId).getOrDefault(session.getTeamId(), new String[]{"Team", ""});
        Map<UUID, String> names = displayNames(eventId, List.of(round.getPlayerA(), round.getPlayerB()));
        List<AlibiPrompt> prompts = promptRepository.findByRoundIdOrderByQuestionNumberAsc(round.getId());
        Map<UUID, Map<String, AlibiAnswer>> answers = answersByPrompt(prompts);

        String brief = null;
        if (PREPARING.equals(round.getStatus()) || QUESTIONING.equals(round.getStatus())) {
            brief = contentOf(round).brief(); // the brief is public by design (GM-019)
        }
        String questionText = null;
        Instant closesAt = null;
        boolean lockedA = false;
        boolean lockedB = false;
        if (QUESTIONING.equals(round.getStatus())) {
            AlibiPrompt prompt = currentPrompt(round);
            questionText = prompt.getQuestionText();
            closesAt = prompt.getClosesAt();
            Map<String, AlibiAnswer> pair = answers.getOrDefault(prompt.getId(), Map.of());
            lockedA = pair.containsKey("A");
            lockedB = pair.containsKey("B");
        }

        List<RevealedItem> revealed = revealedItems(prompts, answers);
        return new PublicView(Instant.now(), new PublicRound(
                team[0],
                team[1],
                round.getPairNumber(),
                names.getOrDefault(round.getPlayerA(), "Player A"),
                names.getOrDefault(round.getPlayerB(), "Player B"),
                round.getStatus(),
                round.getPrepEndsAt(),
                brief,
                round.getCurrentQuestion(),
                round.getQuestionCount(),
                questionText,
                closesAt,
                lockedA,
                lockedB,
                revealed,
                (int) revealed.stream().filter(RevealedItem::match).count()
        ));
    }

    // =================================================================================
    // State machine
    // =================================================================================

    /** Moves a locked round forward past every deadline that has passed. */
    void tick(AlibiRound round, Instant now) {
        if (!QUESTIONING.equals(round.getStatus())) {
            return;
        }
        CaseContent content = contentOf(round);
        while (QUESTIONING.equals(round.getStatus())) {
            AlibiPrompt prompt = currentPrompt(round);
            List<AlibiAnswer> answers = answerRepository.findByPromptId(prompt.getId());
            if (!AlibiGameRules.questionOver(answers.size(), prompt.getClosesAt(), now)) {
                return;
            }
            closePrompt(round, prompt, answers, now, content);
            if (round.getCurrentQuestion() >= round.getQuestionCount()) {
                round.setStatus(JUDGING);
            } else {
                openQuestion(round, round.getCurrentQuestion() + 1, now, content);
            }
            roundRepository.saveAndFlush(round);
        }
    }

    private void openQuestion(AlibiRound round, int number, Instant now, CaseContent content) {
        Question q = questionAt(content, number);
        if (q == null) {
            throw conflict("The case version has no question " + number + ".");
        }
        AlibiPrompt prompt = new AlibiPrompt();
        prompt.setEventId(round.getEventId());
        prompt.setRoundId(round.getId());
        prompt.setQuestionNumber(number);
        prompt.setQuestionText(q.prompt());
        prompt.setOpenedAt(now);
        prompt.setClosesAt(now.plus(AlibiGameRules.QUESTION_TIME));
        promptRepository.saveAndFlush(prompt);
        round.setCurrentQuestion(number);
    }

    /** Records missing answers explicitly, marks the question ready to compare, auto-judges the obvious. */
    private void closePrompt(
            AlibiRound round,
            AlibiPrompt prompt,
            List<AlibiAnswer> answers,
            Instant now,
            CaseContent content
    ) {
        Map<String, AlibiAnswer> bySide = new HashMap<>();
        answers.forEach(a -> bySide.put(a.getSide(), a));
        for (String side : List.of("A", "B")) {
            if (!bySide.containsKey(side)) {
                AlibiAnswer missing = new AlibiAnswer();
                missing.setEventId(round.getEventId());
                missing.setPromptId(prompt.getId());
                missing.setSide(side);
                missing.setUserId("A".equals(side) ? round.getPlayerA() : round.getPlayerB());
                missing.setAnswerText(null);
                missing.setMissing(true);
                answerRepository.saveAndFlush(missing);
                bySide.put(side, missing);
            }
        }

        prompt.setCompareReadyAt(now);
        Question q = questionAt(content, prompt.getQuestionNumber());
        Boolean auto = AlibiGameRules.autoVerdict(
                bySide.get("A").getAnswerText(),
                bySide.get("B").getAnswerText(),
                q == null ? List.of() : q.validEquivalents()
        );
        if (auto != null) {
            prompt.setVerdict(auto);
            prompt.setVerdictSource("auto");
            prompt.setDecidedAt(now);
        }
        promptRepository.saveAndFlush(prompt);
    }

    private boolean isDue(AlibiRound round, Instant now) {
        if (!QUESTIONING.equals(round.getStatus())) {
            return false;
        }
        return promptRepository.findByRoundIdAndQuestionNumber(round.getId(), round.getCurrentQuestion())
                .map(p -> !now.isBefore(p.getClosesAt().plus(AlibiGameRules.GRACE)))
                .orElse(false);
    }

    /** Called by every screen's poll, so time moves on even when nobody answers. */
    private void tickDueRounds(UUID eventId) {
        Instant now = Instant.now();
        for (AlibiRound round : roundRepository.findByEventId(eventId)) {
            if (isDue(round, now)) {
                tick(lockRound(eventId, round.getId()), now);
            }
        }
    }

    // =================================================================================
    // Views
    // =================================================================================

    private HostView buildHostView(UUID eventId) {
        ScoringPlan plan = planRepository.findById(eventId).orElse(null);
        if (plan == null) {
            return new HostView(Instant.now(), false, List.of());
        }
        Map<UUID, String[]> teams = teamInfo(eventId);
        Map<UUID, String> fixtureStatus = fixtureStatuses(eventId);
        Map<UUID, AlibiSession> sessionsByFixture = new HashMap<>();
        sessionRepository.findByEventId(eventId).forEach(s -> sessionsByFixture.put(s.getFixtureId(), s));

        List<TeamSlot> slots = new ArrayList<>();
        for (PlannedSlot slot : plannedSlots(eventId)) {
            String[] team = teams.getOrDefault(slot.teamId(), new String[]{"Team", ""});
            Map<UUID, String> members = teamMembers(eventId, slot.teamId());
            AlibiSession session = sessionsByFixture.get(slot.fixtureId());

            List<RoundHost> rounds = new ArrayList<>();
            int total = 0;
            boolean allDone = session != null;
            Instant lastDecision = null;
            if (session != null) {
                for (AlibiRound round : roundRepository.findBySessionIdOrderByPairNumberAsc(session.getId())) {
                    RoundHost view = roundHost(round, members);
                    rounds.add(view);
                    total += view.matches();
                    allDone &= DONE.equals(round.getStatus());
                    for (AlibiPrompt p : promptRepository.findByRoundIdOrderByQuestionNumberAsc(round.getId())) {
                        if (p.getDecidedAt() != null
                                && (lastDecision == null || p.getDecidedAt().isAfter(lastDecision))) {
                            lastDecision = p.getDecidedAt();
                        }
                    }
                }
            }
            boolean recorded = session != null && session.getRecordedAt() != null;
            boolean needsRecording = allDone && (!recorded
                    || (lastDecision != null && lastDecision.isAfter(session.getRecordedAt())));

            List<CaseOption> options = eligibleCases(eventId, slot.teamId(), null).stream()
                    .map(c -> new CaseOption(c.caseId(), c.label()))
                    .toList();

            slots.add(new TeamSlot(
                    slot.fixtureId(),
                    slot.eventGameId(),
                    slot.teamId(),
                    team[0],
                    team[1],
                    fixtureStatus.getOrDefault(slot.fixtureId(), "scheduled"),
                    session == null ? null : session.getId(),
                    members.entrySet().stream()
                            .map(e -> new Member(e.getKey(), e.getValue()))
                            .sorted(Comparator.comparing(Member::name, String.CASE_INSENSITIVE_ORDER))
                            .toList(),
                    options,
                    rounds,
                    total,
                    recorded,
                    needsRecording
            ));
        }
        return new HostView(Instant.now(), true, slots);
    }

    private RoundHost roundHost(AlibiRound round, Map<UUID, String> members) {
        List<AlibiPrompt> prompts = promptRepository.findByRoundIdOrderByQuestionNumberAsc(round.getId());
        Map<UUID, Map<String, AlibiAnswer>> answers = answersByPrompt(prompts);

        Instant closesAt = null;
        boolean lockedA = false;
        boolean lockedB = false;
        if (QUESTIONING.equals(round.getStatus())) {
            AlibiPrompt current = prompts.stream()
                    .filter(p -> p.getQuestionNumber() == round.getCurrentQuestion())
                    .findFirst().orElse(null);
            if (current != null) {
                closesAt = current.getClosesAt();
                Map<String, AlibiAnswer> pair = answers.getOrDefault(current.getId(), Map.of());
                lockedA = pair.containsKey("A");
                lockedB = pair.containsKey("B");
            }
        }
        int compared = (int) prompts.stream().filter(p -> p.getCompareReadyAt() != null).count();
        int decided = (int) prompts.stream().filter(p -> p.getVerdict() != null).count();
        int matches = AlibiGameRules.score(prompts.stream().map(AlibiPrompt::getVerdict).toList());

        return new RoundHost(
                round.getId(),
                round.getPairNumber(),
                round.getStatus(),
                round.getPlayerA(),
                round.getPlayerA() == null ? null : members.getOrDefault(round.getPlayerA(), "Player"),
                round.getPlayerB(),
                round.getPlayerB() == null ? null : members.getOrDefault(round.getPlayerB(), "Player"),
                round.getCaseId(),
                round.getCaseId() == null ? null : caseLabel(round.getCaseId()),
                round.getPrepEndsAt(),
                round.getCurrentQuestion(),
                round.getQuestionCount(),
                closesAt,
                lockedA,
                lockedB,
                compared,
                decided,
                round.getRevealedCount(),
                matches
        );
    }

    private PlayerRound playerRound(UUID eventId, AlibiRound round, UUID userId) {
        AlibiSession session = sessionOf(round);
        String side = round.sideOf(userId);
        UUID partner = "A".equals(side) ? round.getPlayerB() : round.getPlayerA();
        Map<UUID, String> names = displayNames(eventId, List.of(partner));
        String teamName = teamInfo(eventId).getOrDefault(session.getTeamId(), new String[]{"Team", ""})[0];

        String brief = null;
        List<String> facts = null;
        PlayerQuestion question = null;
        if (PREPARING.equals(round.getStatus())) {
            CaseContent content = contentOf(round);
            brief = content.brief();
            facts = content.fixedFacts();
        }
        List<AlibiPrompt> prompts = promptRepository.findByRoundIdOrderByQuestionNumberAsc(round.getId());
        Map<UUID, Map<String, AlibiAnswer>> answers = answersByPrompt(prompts);
        if (QUESTIONING.equals(round.getStatus())) {
            AlibiPrompt prompt = prompts.stream()
                    .filter(p -> p.getQuestionNumber() == round.getCurrentQuestion())
                    .findFirst().orElse(null);
            if (prompt != null) {
                AlibiAnswer mine = answers.getOrDefault(prompt.getId(), Map.of()).get(side);
                question = new PlayerQuestion(
                        prompt.getId(),
                        prompt.getQuestionNumber(),
                        prompt.getQuestionText(),
                        prompt.getClosesAt(),
                        mine != null,
                        mine != null && mine.isMissing(),
                        mine == null ? null : mine.getAnswerText()
                );
            }
        }
        List<RevealedItem> revealed = revealedItems(prompts, answers);
        return new PlayerRound(
                round.getId(),
                teamName,
                round.getPairNumber(),
                side,
                names.getOrDefault(partner, "your partner"),
                round.getStatus(),
                round.getPrepEndsAt(),
                brief,
                facts,
                question,
                round.getQuestionCount(),
                revealed,
                (int) revealed.stream().filter(RevealedItem::match).count()
        );
    }

    private List<RevealedItem> revealedItems(
            List<AlibiPrompt> prompts,
            Map<UUID, Map<String, AlibiAnswer>> answers
    ) {
        List<RevealedItem> out = new ArrayList<>();
        for (AlibiPrompt prompt : prompts) {
            if (prompt.getRevealedAt() == null) {
                continue;
            }
            Map<String, AlibiAnswer> pair = answers.getOrDefault(prompt.getId(), Map.of());
            out.add(new RevealedItem(
                    prompt.getQuestionNumber(),
                    prompt.getQuestionText(),
                    shown(pair.get("A")),
                    shown(pair.get("B")),
                    Boolean.TRUE.equals(prompt.getVerdict())
            ));
        }
        return out;
    }

    private static String shown(AlibiAnswer answer) {
        if (answer == null || answer.isMissing()) {
            return "(no answer)";
        }
        String text = answer.getAnswerText();
        return text == null || text.isBlank() ? "(blank)" : text;
    }

    private static AnswerView answerView(AlibiAnswer answer) {
        if (answer == null) {
            return new AnswerView(null, true, 0, 0);
        }
        return new AnswerView(answer.getAnswerText(), answer.isMissing(),
                answer.getAwayMs(), answer.getAwayCount());
    }

    // =================================================================================
    // Lookups
    // =================================================================================

    record PlannedSlot(UUID fixtureId, UUID eventGameId, UUID teamId) {
    }

    /** Every Alibi fixture in the published scoring plan: one per team. */
    @SuppressWarnings("unchecked")
    private List<PlannedSlot> plannedSlots(UUID eventId) {
        ScoringPlan plan = planRepository.findById(eventId).orElse(null);
        if (plan == null || plan.getSnapshot() == null) {
            return List.of();
        }
        List<PlannedSlot> out = new ArrayList<>();
        Object games = plan.getSnapshot().get("games");
        if (!(games instanceof List<?> gameList)) {
            return out;
        }
        for (Object g : gameList) {
            if (!(g instanceof Map<?, ?> game)
                    || !SCORING_HANDLER.equals(String.valueOf(game.get("scoringHandler")))) {
                continue;
            }
            UUID eventGameId = UUID.fromString(String.valueOf(game.get("eventGameId")));
            Object fixtures = game.get("fixtures");
            if (!(fixtures instanceof List<?> fixtureList)) {
                continue;
            }
            for (Object f : fixtureList) {
                if (!(f instanceof Map<?, ?> fixture) || Boolean.TRUE.equals(fixture.get("bye"))) {
                    continue;
                }
                Object teamIds = fixture.get("teamIds");
                if (teamIds instanceof List<?> ids && ids.size() == 1) {
                    out.add(new PlannedSlot(
                            UUID.fromString(String.valueOf(fixture.get("id"))),
                            eventGameId,
                            UUID.fromString(String.valueOf(ids.getFirst()))
                    ));
                }
            }
        }
        return out;
    }

    /**
     * Ready cases never used in this event and unknown to every member of the team.
     * {@code ignoreRoundId}: a round being set up may keep or change its own case.
     */
    private List<CaseCandidate> eligibleCases(UUID eventId, UUID teamId, UUID ignoreRoundId) {
        Set<UUID> used = new HashSet<>();
        for (AlibiRound r : roundRepository.findByEventId(eventId)) {
            if (r.getCaseId() != null && !r.getId().equals(ignoreRoundId)) {
                used.add(r.getCaseId());
            }
        }

        Map<UUID, Set<UUID>> knownBy = caseViewers(eventId);
        List<CaseCandidate> pool = new ArrayList<>();
        for (AlibiCase c : caseRepository.findByEventIdAndArchivedAtIsNullOrderBySortOrderAscCreatedAtAsc(eventId)) {
            boolean ready = c.getCurrentVersion() > 0 && versionRepository
                    .findByCaseIdAndVersionNumber(c.getId(), c.getCurrentVersion())
                    .map(v -> AlibiCaseRules.isReady(AlibiCaseRules.fromMap(v.getContent())))
                    .orElse(false);
            pool.add(new CaseCandidate(c.getId(), c.getLabel(), ready,
                    knownBy.getOrDefault(c.getId(), Set.of())));
        }
        return AlibiGameRules.eligible(pool, used, teamMembers(eventId, teamId).keySet());
    }

    private boolean mayJudge(UUID eventId, UUID userId, AlibiSession session) {
        // Nobody judges their own team, organizers included.
        if (session.getTeamId().equals(activeTeamOf(eventId, userId))) {
            return false;
        }
        try {
            refereeService.requireCanRecordResult(
                    eventId, userId, session.getEventGameId(), List.of(session.getTeamId()));
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    /** The round this player is in: the active one, else the most recent. */
    private AlibiRound myRound(UUID eventId, UUID userId) {
        AlibiRound best = null;
        for (AlibiRound r : roundRepository.findByEventId(eventId)) {
            if (r.sideOf(userId) == null) {
                continue;
            }
            if (best == null || rank(r) > rank(best)
                    || (rank(r) == rank(best) && newer(r, best))) {
                best = r;
            }
        }
        return best;
    }

    /** What the TV shows: the round being played, else the last one being revealed. */
    private AlibiRound featuredRound(UUID eventId) {
        AlibiRound best = null;
        for (AlibiRound r : roundRepository.findByEventId(eventId)) {
            if (!STARTED.contains(r.getStatus())) {
                continue;
            }
            if (best == null || rank(r) > rank(best)
                    || (rank(r) == rank(best) && newer(r, best))) {
                best = r;
            }
        }
        return best;
    }

    private static boolean newer(AlibiRound a, AlibiRound b) {
        Instant ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getStartedAt();
        Instant tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getStartedAt();
        if (ta == null) {
            return false;
        }
        return tb == null || ta.isAfter(tb);
    }

    private static int rank(AlibiRound r) {
        return switch (r.getStatus()) {
            case PREPARING, QUESTIONING -> 4;
            case JUDGING -> 3;
            case SETUP -> 2;
            default -> 1;
        };
    }

    private List<AlibiRound> startedRounds(UUID eventId) {
        return roundRepository.findByEventId(eventId).stream()
                .filter(r -> STARTED.contains(r.getStatus()))
                .sorted(Comparator.comparing(AlibiRound::getStartedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Map<UUID, AlibiSession> sessionsById(UUID eventId) {
        Map<UUID, AlibiSession> map = new HashMap<>();
        sessionRepository.findByEventId(eventId).forEach(s -> map.put(s.getId(), s));
        return map;
    }

    private AlibiSession sessionOf(AlibiRound round) {
        return sessionRepository.findByIdAndEventId(round.getSessionId(), round.getEventId())
                .orElseThrow(() -> new EntityNotFoundException("Alibi session not found."));
    }

    private AlibiRound lockRound(UUID eventId, UUID roundId) {
        return roundRepository.lock(eventId, roundId)
                .orElseThrow(() -> new EntityNotFoundException("Round not found."));
    }

    private AlibiPrompt currentPrompt(AlibiRound round) {
        return promptRepository.findByRoundIdAndQuestionNumber(round.getId(), round.getCurrentQuestion())
                .orElseThrow(() -> conflict("The current question is missing."));
    }

    private CaseContent contentOf(AlibiRound round) {
        if (round.getCaseVersionId() == null) {
            return CaseContent.empty();
        }
        return versionRepository.findById(round.getCaseVersionId())
                .map(v -> AlibiCaseRules.fromMap(v.getContent()))
                .orElse(CaseContent.empty());
    }

    private static Question questionAt(CaseContent content, int number) {
        if (number < 1 || number > content.questions().size()) {
            return null;
        }
        return content.questions().get(number - 1);
    }

    private String caseLabel(UUID caseId) {
        if (caseId == null) {
            return null;
        }
        return caseRepository.findById(caseId).map(AlibiCase::getLabel).orElse("Case");
    }

    private int matchesOf(UUID roundId) {
        return AlibiGameRules.score(promptRepository.findByRoundIdOrderByQuestionNumberAsc(roundId)
                .stream().map(AlibiPrompt::getVerdict).toList());
    }

    private Map<UUID, Map<String, AlibiAnswer>> answersByPrompt(List<AlibiPrompt> prompts) {
        Map<UUID, Map<String, AlibiAnswer>> map = new HashMap<>();
        if (prompts.isEmpty()) {
            return map;
        }
        for (AlibiAnswer a : answerRepository.findByPromptIdIn(prompts.stream().map(AlibiPrompt::getId).toList())) {
            map.computeIfAbsent(a.getPromptId(), k -> new HashMap<>()).put(a.getSide(), a);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String[]> teamInfo(UUID eventId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select id, name, color from murise.teams where event_id = :eventId
                        """)
                .setParameter("eventId", eventId)
                .getResultList();
        Map<UUID, String[]> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(UUID.fromString(row[0].toString()),
                    new String[]{String.valueOf(row[1]), row[2] == null ? "" : row[2].toString()});
        }
        return map;
    }

    /** Active members of a team who have an account (they need a phone to play). */
    @SuppressWarnings("unchecked")
    private Map<UUID, String> teamMembers(UUID eventId, UUID teamId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select ep.user_id, coalesce(ep.display_name, p.display_name)
                        from murise.event_participants ep
                        join murise.profiles p on p.id = ep.user_id
                        where ep.event_id = :eventId
                          and ep.team_id = :teamId
                          and ep.status = 'active'
                          and ep.user_id is not null
                        """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", teamId)
                .getResultList();
        Map<UUID, String> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put(UUID.fromString(row[0].toString()), String.valueOf(row[1]));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private UUID activeTeamOf(UUID eventId, UUID userId) {
        List<Object> rows = entityManager.createNativeQuery("""
                        select team_id from murise.event_participants
                        where event_id = :eventId and user_id = :userId
                          and status = 'active' and team_id is not null
                        """)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getResultList();
        return rows.isEmpty() ? null : UUID.fromString(rows.getFirst().toString());
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> displayNames(UUID eventId, Collection<UUID> userIds) {
        List<UUID> ids = userIds.stream().filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select p.id, coalesce(ep.display_name, p.display_name)
                        from murise.profiles p
                        left join murise.event_participants ep
                          on ep.user_id = p.id and ep.event_id = :eventId and ep.status = 'active'
                        where p.id in (:ids)
                        """)
                .setParameter("eventId", eventId)
                .setParameter("ids", ids)
                .getResultList();
        Map<UUID, String> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(UUID.fromString(row[0].toString()), String.valueOf(row[1]));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Set<UUID>> caseViewers(UUID eventId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select case_id, user_id from murise.alibi_case_viewers where event_id = :eventId
                        """)
                .setParameter("eventId", eventId)
                .getResultList();
        Map<UUID, Set<UUID>> map = new HashMap<>();
        for (Object[] row : rows) {
            map.computeIfAbsent(UUID.fromString(row[0].toString()), k -> new HashSet<>())
                    .add(UUID.fromString(row[1].toString()));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> fixtureStatuses(UUID eventId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select id, status from murise.fixtures where event_id = :eventId
                        """)
                .setParameter("eventId", eventId)
                .getResultList();
        Map<UUID, String> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(UUID.fromString(row[0].toString()), String.valueOf(row[1]));
        }
        return map;
    }

    private void lockEvent(UUID eventId) {
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
