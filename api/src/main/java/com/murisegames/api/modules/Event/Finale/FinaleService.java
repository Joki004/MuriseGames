package com.murisegames.api.modules.Event.Finale;

import com.murisegames.api.modules.Audit.AuditService;
import com.murisegames.api.modules.Event.Clue.ClueReleaseService;
import com.murisegames.api.modules.Event.Clue.SceneEntry;
import com.murisegames.api.modules.Event.Clue.SceneEntryRepository;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Finale.FinaleScoring.Award;
import com.murisegames.api.modules.Event.Finale.FinaleScoring.Category;
import com.murisegames.api.modules.Event.Finale.FinaleScoring.GuessRecord;
import com.murisegames.api.modules.Event.Finale.FinaleScoring.Rules;
import com.murisegames.api.modules.Event.Finale.FinaleScoring.SceneSettlement;
import com.murisegames.api.modules.Event.Finale.FinaleScoring.Verdict;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Scoring.ScoreEntryRepository;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.Event.Title.SceneSecret;
import com.murisegames.api.modules.Event.Title.SceneSecretRepository;
import com.murisegames.api.modules.Event.Title.TitleAlias;
import com.murisegames.api.modules.Event.Title.TitleAliasRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.murisegames.api.modules.Event.Finale.FinaleTypes.*;

/**
 * GM-018: host review of guesses, host-commanded title reveals and one-time
 * settlement of guessing points + performer bonuses into the score ledger.
 */
@Service
@RequiredArgsConstructor
public class FinaleService {

    static final String CATEGORY_GUESS = "guess_points";
    static final String CATEGORY_PERFORMER = "performer_bonus";

    private final ClueReleaseService clueReleaseService;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final SceneEntryRepository sceneEntryRepository;
    private final SceneSecretRepository sceneSecretRepository;
    private final TitleAliasRepository titleAliasRepository;
    private final TeamRepository teamRepository;
    private final ScoreEntryRepository scoreEntryRepository;
    private final AuditService auditService;
    private final EntityManager entityManager;

    // =====================================================================
    // Host review (organizer only)
    // =====================================================================

    @Transactional(readOnly = true)
    public ReviewBoardResponse reviewBoard(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        return buildReviewBoard(eventId);
    }

    @Transactional
    public ReviewBoardResponse decide(
            Jwt jwt,
            UUID eventId,
            UUID guessId,
            ReviewDecisionRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        // Same event row lock as reveal, so a decision can't race a reveal.
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        UUID sceneId = findGuessScene(eventId, guessId);
        if (isRevealed(eventId, sceneId)) {
            throw conflict("This title has already been revealed. Use a score correction instead.");
        }

        entityManager.createNativeQuery("""
                INSERT INTO murise.guess_reviews (guess_id, event_id, verdict, decided_by)
                VALUES (:guessId, :eventId, :verdict, :actorId)
                ON CONFLICT (guess_id) DO UPDATE
                SET verdict    = EXCLUDED.verdict,
                    decided_by = EXCLUDED.decided_by,
                    decided_at = now()
                """)
                .setParameter("guessId", guessId)
                .setParameter("eventId", eventId)
                .setParameter("verdict", request.verdict())
                .setParameter("actorId", actorId)
                .executeUpdate();

        auditService.record(
                eventId, actorId, "guess.reviewed", "guess", guessId,
                Map.of("verdict", request.verdict())
        );

        return buildReviewBoard(eventId);
    }

    // =====================================================================
    // Reveals
    // =====================================================================

    /** Everyone in the event may read reveals; only revealed titles are ever returned. */
    @Transactional(readOnly = true)
    public RevealListResponse listReveals(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, actorId);

        @SuppressWarnings("unchecked")
        List<Object> sceneIds = entityManager.createNativeQuery("""
                SELECT scene_id
                FROM murise.scene_reveals
                WHERE event_id = :eventId
                ORDER BY revealed_at ASC
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        Context ctx = loadContext(eventId);
        List<RevealView> reveals = sceneIds.stream()
                .map(id -> buildReveal(eventId, toUuid(id), ctx))
                .toList();

        return new RevealListResponse(readGuessingStatus(eventId), reveals);
    }

    /**
     * Reveals one team's title and settles its guessing points and performer bonus once.
     * Refused while guessing is open or while any guess for this title awaits a host decision.
     * Repeating the command (or a rehearsal) returns the same reveal and awards nothing new.
     */
    @Transactional
    public RevealView reveal(Jwt jwt, UUID eventId, RevealRequest request) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        // Locks the event + guessing_state rows; 409 unless all guessing is closed.
        clueReleaseService.lockForReveal(eventId);

        SceneEntry scene = sceneEntryRepository.findByEventIdAndTeamId(eventId, request.teamId())
                .orElseThrow(() -> new EntityNotFoundException("This team has no secret scene."));
        UUID sceneId = scene.getId();

        sceneSecretRepository.findById(sceneId)
                .orElseThrow(() -> conflict("This team has not registered a title."));

        Context ctx = loadContext(eventId);

        if (isRevealed(eventId, sceneId)) {
            return buildReveal(eventId, sceneId, ctx);
        }

        UUID sceneForRequest = findSceneForRevealRequest(eventId, request.requestId());
        if (sceneForRequest != null && !sceneForRequest.equals(sceneId)) {
            throw conflict("This request was already used to reveal another title.");
        }

        List<GuessRecord> records = loadGuessesForScene(eventId, sceneId).stream()
                .map(g -> new GuessRecord(
                        g.guessId(), g.guessingTeamId(), sceneId, g.stage(),
                        verdictFor(g, sceneId, ctx)
                ))
                .toList();

        SceneSettlement settlement;
        try {
            settlement = FinaleScoring.settleScene(
                    eventId, sceneId, scene.getTeamId(), records, Rules.DEFAULT
            );
        } catch (IllegalStateException ex) {
            throw conflict(ex.getMessage());
        }

        for (Award award : settlement.awards()) {
            writeLedgerEntry(eventId, actorId, award, ctx);
        }

        entityManager.createNativeQuery("""
                INSERT INTO murise.scene_reveals (scene_id, event_id, request_id, revealed_by)
                VALUES (:sceneId, :eventId, :requestId, :actorId)
                """)
                .setParameter("sceneId", sceneId)
                .setParameter("eventId", eventId)
                .setParameter("requestId", request.requestId())
                .setParameter("actorId", actorId)
                .executeUpdate();

        auditService.record(
                eventId, actorId, "scene.revealed", "scene_entry", sceneId,
                Map.of(
                        "teamId", scene.getTeamId().toString(),
                        "correctTeams", settlement.correctGuessers().size(),
                        "awards", settlement.awards().size(),
                        "ruleVersion", FinaleScoring.RULE_VERSION
                )
        );

        return buildReveal(eventId, sceneId, ctx);
    }

    /**
     * Slide 14 notes: an exact title leaked before the lock gives every other team +2,
     * on top of their guessing points. Recording the same leak again awards nothing new.
     * Allowed until that title has been revealed.
     */
    @Transactional
    public ReviewBoardResponse recordLeak(Jwt jwt, UUID eventId, LeakRequest request) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        SceneEntry scene = sceneEntryRepository.findByEventIdAndTeamId(eventId, request.teamId())
                .orElseThrow(() -> new EntityNotFoundException("This team has no secret scene."));
        UUID sceneId = scene.getId();

        sceneSecretRepository.findById(sceneId)
                .orElseThrow(() -> conflict("This team has not registered a title."));

        if (isRevealed(eventId, sceneId)) {
            throw conflict("This title has already been revealed. Use a score correction instead.");
        }

        Context ctx = loadContext(eventId);
        List<Award> awards = FinaleScoring.leakAwards(
                eventId, sceneId, scene.getTeamId(), ctx.teams().keySet(), Rules.DEFAULT
        );
        for (Award award : awards) {
            writeLedgerEntry(eventId, actorId, award, ctx);
        }

        auditService.record(
                eventId, actorId, "scene.leaked", "scene_entry", sceneId,
                Map.of(
                        "teamId", scene.getTeamId().toString(),
                        "awardedTeams", awards.size(),
                        "ruleVersion", FinaleScoring.RULE_VERSION
                )
        );

        return buildReviewBoard(eventId);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private void writeLedgerEntry(UUID eventId, UUID actorId, Award award, Context ctx) {
        // UNIQUE(event_id, source_key) is the real guard; this check keeps retries quiet.
        if (scoreEntryRepository.existsByEventIdAndSourceKey(eventId, award.idempotencyKey())) {
            return;
        }

        // A leak bonus is guessing points too (agreed 23 Nov 2026: added on top).
        String category = award.category() == Category.PERFORMER ? CATEGORY_PERFORMER : CATEGORY_GUESS;
        String sourceTeam = ctx.teamName(award.sourceTeamId());
        String performer = ctx.teamName(ctx.teamByScene().get(award.sceneId()));
        String reason = switch (award.category()) {
            case GUESS -> "Secret Scene: identified " + performer + "'s title at stage " + award.stage();
            case PERFORMER -> "Secret Scene: " + sourceTeam + " identified your title after clue " + award.stage();
            case LEAK -> "Secret Scene: " + performer + "'s title leaked before the lock";
        };

        // Leak awards have no guess; leave guess_id out rather than binding a typeless null.
        boolean hasGuess = award.sourceGuessId() != null;
        var insert = entityManager.createNativeQuery(hasGuess
                        ? """
                        INSERT INTO murise.score_entries (
                            event_id, team_id, category, delta, source_key, reason, actor_id, guess_id
                        )
                        VALUES (
                            :eventId, :teamId, :category, :delta, :sourceKey, :reason, :actorId, :guessId
                        )
                        """
                        : """
                        INSERT INTO murise.score_entries (
                            event_id, team_id, category, delta, source_key, reason, actor_id
                        )
                        VALUES (
                            :eventId, :teamId, :category, :delta, :sourceKey, :reason, :actorId
                        )
                        """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", award.teamId())
                .setParameter("category", category)
                .setParameter("delta", award.points())
                .setParameter("sourceKey", award.idempotencyKey())
                .setParameter("reason", reason)
                .setParameter("actorId", actorId);
        if (hasGuess) {
            insert.setParameter("guessId", award.sourceGuessId());
        }
        insert.executeUpdate();
    }

    private RevealView buildReveal(UUID eventId, UUID sceneId, Context ctx) {
        UUID performerTeamId = ctx.teamByScene().get(sceneId);
        SceneSecret secret = ctx.secretByScene().get(sceneId);

        Instant revealedAt = toInstant(entityManager.createNativeQuery("""
                SELECT revealed_at
                FROM murise.scene_reveals
                WHERE event_id = :eventId AND scene_id = :sceneId
                """)
                .setParameter("eventId", eventId)
                .setParameter("sceneId", sceneId)
                .getSingleResult());

        // Read what was actually awarded (minus any later reversal), not a recomputation.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT se.category, se.delta, g.guessing_team_id, g.stage
                FROM murise.score_entries se
                JOIN murise.guesses g ON g.id = se.guess_id
                WHERE se.event_id = :eventId
                  AND g.target_scene_id = :sceneId
                  AND se.category IN ('guess_points', 'performer_bonus')
                  AND NOT EXISTS (
                      SELECT 1 FROM murise.score_entries r WHERE r.reverses_entry_id = se.id
                  )
                ORDER BY g.stage ASC, g.submitted_at ASC
                """)
                .setParameter("eventId", eventId)
                .setParameter("sceneId", sceneId)
                .getResultList();

        List<CorrectGuesserView> correct = new ArrayList<>();
        BigDecimal performerBonus = BigDecimal.ZERO;
        for (Object[] r : rows) {
            BigDecimal delta = new BigDecimal(r[1].toString());
            if (CATEGORY_GUESS.equals(r[0].toString())) {
                UUID teamId = toUuid(r[2]);
                Team team = ctx.teams().get(teamId);
                correct.add(new CorrectGuesserView(
                        teamId,
                        ctx.teamName(teamId),
                        team != null ? team.getColor() : "#64748b",
                        ((Number) r[3]).intValue(),
                        delta.stripTrailingZeros()
                ));
            } else {
                performerBonus = performerBonus.add(delta);
            }
        }

        Team performer = ctx.teams().get(performerTeamId);
        boolean leaked = loadLeakedScenes(eventId).contains(sceneId);
        return new RevealView(
                sceneId,
                performerTeamId,
                ctx.teamName(performerTeamId),
                performer != null ? performer.getColor() : "#64748b",
                secret != null ? secret.getTitle() : null,
                secret != null ? secret.getAnswerScope() : null,
                revealedAt,
                correct,
                performerBonus.stripTrailingZeros(),
                leaked
        );
    }

    private ReviewBoardResponse buildReviewBoard(UUID eventId) {
        Context ctx = loadContext(eventId);
        Set<UUID> revealed = loadRevealedScenes(eventId);

        List<ReviewItem> items = loadAllGuesses(eventId).stream()
                .map(g -> {
                    UUID targetTeamId = ctx.teamByScene().get(g.sceneId());
                    SceneSecret secret = ctx.secretByScene().get(g.sceneId());
                    boolean autoMatched = FinaleScoring.verdictFor(
                            g.rawGuess(),
                            secret != null ? secret.getNormalizedTitle() : "",
                            ctx.aliasesByScene().getOrDefault(g.sceneId(), Set.of()),
                            null
                    ) == Verdict.CORRECT;

                    return new ReviewItem(
                            g.guessId(),
                            g.guessingTeamId(),
                            ctx.teamName(g.guessingTeamId()),
                            targetTeamId,
                            ctx.teamName(targetTeamId),
                            secret != null ? secret.getTitle() : null,
                            g.stage(),
                            g.rawGuess(),
                            g.submittedAt(),
                            autoMatched,
                            g.hostDecision(),
                            toVerdictLabel(verdictFor(g, g.sceneId(), ctx)),
                            revealed.contains(g.sceneId())
                    );
                })
                .sorted(Comparator
                        .comparing(ReviewItem::targetTeamName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(ReviewItem::stage)
                        .thenComparing(ReviewItem::submittedAt))
                .toList();

        int pending = (int) items.stream().filter(i -> "pending".equals(i.verdict())).count();
        List<UUID> leakedTeamIds = loadLeakedScenes(eventId).stream()
                .map(sceneId -> ctx.teamByScene().get(sceneId))
                .filter(Objects::nonNull)
                .toList();
        return new ReviewBoardResponse(readGuessingStatus(eventId), pending, items, leakedTeamIds);
    }

    private Verdict verdictFor(StoredGuess g, UUID sceneId, Context ctx) {
        SceneSecret secret = ctx.secretByScene().get(sceneId);
        return FinaleScoring.verdictFor(
                g.rawGuess(),
                secret != null ? secret.getNormalizedTitle() : "",
                ctx.aliasesByScene().getOrDefault(sceneId, Set.of()),
                g.hostDecision()
        );
    }

    static String toVerdictLabel(Verdict verdict) {
        return switch (verdict) {
            case CORRECT -> "correct";
            case WRONG, BLANK -> "wrong";
            case PENDING_REVIEW -> "pending";
        };
    }

    private Context loadContext(UUID eventId) {
        Map<UUID, Team> teams = teamRepository
                .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId)
                .stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));

        Map<UUID, UUID> teamByScene = sceneEntryRepository.findByEventId(eventId)
                .stream()
                .collect(Collectors.toMap(SceneEntry::getId, SceneEntry::getTeamId));

        Map<UUID, SceneSecret> secretByScene = sceneSecretRepository.findByEventId(eventId)
                .stream()
                .collect(Collectors.toMap(SceneSecret::getSceneId, Function.identity()));

        Map<UUID, Set<String>> aliasesByScene = titleAliasRepository.findByEventId(eventId)
                .stream()
                .collect(Collectors.groupingBy(
                        TitleAlias::getSceneId,
                        Collectors.mapping(TitleAlias::getNormalizedAlias, Collectors.toSet())
                ));

        return new Context(teams, teamByScene, secretByScene, aliasesByScene);
    }

    private List<StoredGuess> loadGuessesForScene(UUID eventId, UUID sceneId) {
        return loadAllGuesses(eventId).stream()
                .filter(g -> g.sceneId().equals(sceneId))
                .toList();
    }

    private List<StoredGuess> loadAllGuesses(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT g.id, g.guessing_team_id, g.target_scene_id, g.stage,
                       g.raw_guess, g.submitted_at, r.verdict
                FROM murise.guesses g
                LEFT JOIN murise.guess_reviews r ON r.guess_id = g.id
                WHERE g.event_id = :eventId
                ORDER BY g.stage ASC, g.submitted_at ASC
                """)
                .setParameter("eventId", eventId)
                .getResultList();

        return rows.stream()
                .map(r -> new StoredGuess(
                        toUuid(r[0]),
                        toUuid(r[1]),
                        toUuid(r[2]),
                        ((Number) r[3]).intValue(),
                        r[4].toString(),
                        toInstant(r[5]),
                        r[6] != null ? r[6].toString() : null
                ))
                .toList();
    }

    private UUID findGuessScene(UUID eventId, UUID guessId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT target_scene_id
                FROM murise.guesses
                WHERE event_id = :eventId AND id = :guessId
                """)
                .setParameter("eventId", eventId)
                .setParameter("guessId", guessId)
                .getResultList();

        if (rows.isEmpty()) {
            throw new EntityNotFoundException("Guess not found in this event.");
        }
        return toUuid(rows.getFirst());
    }

    private boolean isRevealed(UUID eventId, UUID sceneId) {
        Number count = (Number) entityManager.createNativeQuery("""
                SELECT count(*)
                FROM murise.scene_reveals
                WHERE event_id = :eventId AND scene_id = :sceneId
                """)
                .setParameter("eventId", eventId)
                .setParameter("sceneId", sceneId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private Set<UUID> loadRevealedScenes(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT scene_id FROM murise.scene_reveals WHERE event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();
        return rows.stream().map(FinaleService::toUuid).collect(Collectors.toSet());
    }

    /** Scenes with a recorded leak, read from the ledger keys "finale:{event}:{scene}:LEAK:{team}". */
    private Set<UUID> loadLeakedScenes(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT DISTINCT split_part(source_key, ':', 3)
                FROM murise.score_entries
                WHERE event_id = :eventId
                  AND source_key LIKE :prefix
                  AND split_part(source_key, ':', 4) = 'LEAK'
                """)
                .setParameter("eventId", eventId)
                .setParameter("prefix", "finale:" + eventId + ":%")
                .getResultList();
        return rows.stream().map(FinaleService::toUuid).collect(Collectors.toSet());
    }

    private UUID findSceneForRevealRequest(UUID eventId, UUID requestId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT scene_id
                FROM murise.scene_reveals
                WHERE event_id = :eventId AND request_id = :requestId
                """)
                .setParameter("eventId", eventId)
                .setParameter("requestId", requestId)
                .getResultList();
        return rows.isEmpty() ? null : toUuid(rows.getFirst());
    }

    private String readGuessingStatus(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT status FROM murise.guessing_state WHERE event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();
        return rows.isEmpty() ? "open" : rows.getFirst().toString();
    }

    private static UUID toUuid(Object value) {
        return value instanceof UUID u ? u : UUID.fromString(value.toString());
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(value.toString());
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record StoredGuess(
            UUID guessId,
            UUID guessingTeamId,
            UUID sceneId,
            int stage,
            String rawGuess,
            Instant submittedAt,
            String hostDecision
    ) {
    }

    private record Context(
            Map<UUID, Team> teams,
            Map<UUID, UUID> teamByScene,
            Map<UUID, SceneSecret> secretByScene,
            Map<UUID, Set<String>> aliasesByScene
    ) {
        String teamName(UUID teamId) {
            Team team = teamId == null ? null : teams.get(teamId);
            return team != null ? team.getName() : "Unknown team";
        }
    }
}
