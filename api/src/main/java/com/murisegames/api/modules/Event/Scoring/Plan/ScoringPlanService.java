package com.murisegames.api.modules.Event.Scoring.Plan;

import com.murisegames.api.modules.Catalog.GameVersionRepository;
import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Scoring.Adapter.GameScoreAdapters;
import com.murisegames.api.modules.Event.Scoring.Adapter.ScorePreviewService;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.murisegames.api.modules.Event.Scoring.Plan.ScoringPlanTypes.*;

@Service
@RequiredArgsConstructor
public class ScoringPlanService {

    private static final String ADAPTER_REVISION = "policy-a-1";

    private static final String PRECISION =
            "Exact fractions for ranking; sum before rounding; "
                    + "HALF_UP to one decimal for display and four decimals "
                    + "for settlement; exact ties share rank.";

    private final ScoringPlanRepository planRepository;
    private final EventRepository eventRepository;
    private final TeamRepository teamRepository;
    private final GameVersionRepository versionRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final ScorePreviewService previewService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Setup setup(Jwt jwt, UUID eventId) {
        membershipService.requireOrganizer(
                eventId, userService.extractUserId(jwt)
        );

        List<Draft> drafts = rows("""
                SELECT id, version, mode
                FROM murise.schedule_versions
                WHERE event_id = :eventId AND status = 'draft'
                ORDER BY version DESC
                """, "eventId", eventId)
                .stream()
                .map(row -> new Draft(
                        uuid(row[0]),
                        ((Number) row[1]).intValue(),
                        row[2].toString()
                ))
                .toList();

        return new Setup(
                drafts,
                previewService.options(jwt, eventId),
                planRepository.findById(eventId)
                        .map(this::published)
                        .orElse(null)
        );
    }

    /*
     * This takes a short event lock but makes no persistent changes.
     * Source-table triggers use the same lock, so one review sees one
     * consistent set of teams, fixtures and rules.
     */
    @Transactional
    public Review review(Jwt jwt, UUID eventId, Selection selection) {
        Event event = lockOrganizer(jwt, eventId);

        if (planRepository.existsById(eventId)) {
            throw conflict("This event already has a published scoring plan.");
        }

        return buildReview(jwt, event, selection);
    }

    @Transactional
    public Published publish(
            Jwt jwt,
            UUID eventId,
            PublishRequest request
    ) {
        Event event = lockOrganizer(jwt, eventId);
        UUID actorId = userService.extractUserId(jwt);

        validateSelection(request.selection());

        String note = request.decisionNote().strip();
        if (note.length() < 20) {
            throw badRequest(
                    "Record the rule decisions and approval in at least 20 characters."
            );
        }

        String commandHash = hash(
                eventId + "|" + request.selection().scheduleId() + "|"
                        + sortedIds(request.selection().eventGameIds()) + "|"
                        + request.reviewHash() + "|"
                        + sortedIds(request.approvedVersionIds()) + "|"
                        + request.approvePolicy() + "|" + note
        );

        // Check retries before checking draft status: a successful first
        // attempt has already changed that status to published.
        var existing = planRepository.findById(eventId);
        if (existing.isPresent()) {
            ScoringPlan plan = existing.get();

            if (plan.getRequestId().equals(request.requestId())
                    && plan.getApprovedBy().equals(actorId)
                    && plan.getRequestHash().equals(commandHash)) {
                return published(plan);
            }

            throw conflict(
                    "A scoring plan is already published. Refresh to view it."
            );
        }

        Review review = buildReview(jwt, event, request.selection());

        if (!review.reviewHash().equals(request.reviewHash())) {
            throw conflict(
                    "The event or schedule changed after your review. "
                            + "Review the updated plan before publishing."
            );
        }

        if (!review.blockers().isEmpty()) {
            throw conflict(review.blockers().getFirst());
        }

        Set<UUID> requiredVersions = review.games().stream()
                .map(GameView::gameVersionId)
                .collect(Collectors.toSet());

        Set<UUID> approvedVersions =
                new HashSet<>(request.approvedVersionIds());

        if (!request.approvePolicy()
                || approvedVersions.size() != request.approvedVersionIds().size()
                || !approvedVersions.equals(requiredVersions)) {
            throw badRequest(
                    "Approve Policy A and every included rule version."
            );
        }

        int changed = query("""
                UPDATE murise.schedule_versions
                SET status = 'published'
                WHERE event_id = :eventId
                  AND id = :scheduleId
                  AND status = 'draft'
                """,
                "eventId", eventId,
                "scheduleId", review.scheduleId()
        ).executeUpdate();

        if (changed != 1) {
            throw conflict("The schedule changed. Review it again.");
        }

        ScoringPlan plan = new ScoringPlan();
        plan.setEventId(eventId);
        plan.setScheduleId(review.scheduleId());
        plan.setRequestId(request.requestId());
        plan.setRequestHash(commandHash);
        plan.setApprovedBy(actorId);
        plan.setDecisionNote(note);
        plan.setSnapshot(snapshot(review));

        plan = planRepository.saveAndFlush(plan);
        entityManager.refresh(plan);

        query("""
                INSERT INTO murise.audit_events (
                    event_id, actor_id, action, entity_type, entity_id, details
                )
                VALUES (
                    :eventId, :actorId, 'SCORING_PLAN_PUBLISHED',
                    'scoring_plan', :eventId,
                    jsonb_build_object(
                        'policy', 'A',
                        'scheduleId', CAST(:scheduleId AS text),
                        'reviewHash', CAST(:reviewHash AS text)
                    )
                )
                """,
                "eventId", eventId,
                "actorId", actorId,
                "scheduleId", review.scheduleId(),
                "reviewHash", review.reviewHash()
        ).executeUpdate();

        return published(plan);
    }

    private Review buildReview(
            Jwt jwt,
            Event event,
            Selection selection
    ) {
        validateSelection(selection);

        UUID eventId = event.getId();
        UUID scheduleId = selection.scheduleId();

        List<Object[]> schedules = rows("""
                SELECT version, mode, status
                FROM murise.schedule_versions
                WHERE event_id = :eventId AND id = :scheduleId
                """, "eventId", eventId, "scheduleId", scheduleId);

        if (schedules.isEmpty()) {
            throw new EntityNotFoundException("Draft schedule not found.");
        }

        Object[] schedule = schedules.getFirst();
        List<String> blockers = new ArrayList<>();

        if (!Set.of("draft", "published").contains(event.getStatus())) {
            blockers.add("Publish the scoring plan before the event goes live.");
        }

        if (!"draft".equals(schedule[2].toString())) {
            blockers.add("Choose an unpublished draft schedule.");
        }

        if (!Set.of("manual", "round_robin").contains(schedule[1].toString())) {
            blockers.add(
                    "Policy A currently accepts manual or round-robin schedules. "
                            + "Balanced subsets need a separately approved policy."
            );
        }

        long otherPublished = count("""
                SELECT count(*)
                FROM murise.schedule_versions
                WHERE event_id = :eventId
                  AND status = 'published'
                  AND id <> :scheduleId
                """, "eventId", eventId, "scheduleId", scheduleId);

        if (otherPublished > 0) {
            blockers.add(
                    "Another schedule is already published. "
                            + "An audited schedule amendment is required."
            );
        }

        long priorResults = count("""
                SELECT
                    (SELECT count(*) FROM murise.score_entries
                     WHERE event_id = :eventId)
                  + (SELECT count(*) FROM murise.round_results
                     WHERE event_id = :eventId)
                  + (SELECT count(*) FROM murise.fixture_results
                     WHERE event_id = :eventId)
                  + (SELECT count(*) FROM murise.game_results
                     WHERE event_id = :eventId)
                  + (SELECT count(*) FROM murise.rounds
                     WHERE event_id = :eventId AND status <> 'scheduled')
                """, "eventId", eventId);

        if (priorResults > 0) {
            blockers.add(
                    "This event already has play history or scores. "
                            + "Do not create a scoring plan retroactively."
            );
        }

        long dependencies = count("""
                SELECT count(*)
                FROM murise.fixture_dependencies d
                JOIN murise.fixtures f
                  ON f.event_id = d.event_id
                 AND f.id IN (d.source_fixture_id, d.target_fixture_id)
                WHERE f.event_id = :eventId
                  AND f.schedule_version_id = :scheduleId
                """, "eventId", eventId, "scheduleId", scheduleId);

        if (dependencies > 0) {
            blockers.add(
                    "This schedule contains advancement dependencies. "
                            + "Tournament scoring is not enabled under this policy."
            );
        }

        List<TeamView> teams = teamRepository
                .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId)
                .stream()
                .map(team -> new TeamView(team.getId(), team.getName()))
                .toList();

        Set<UUID> teamIds = teams.stream()
                .map(TeamView::id)
                .collect(Collectors.toSet());

        if (teams.isEmpty()) {
            blockers.add("Add active teams before publishing.");
        }

        Map<UUID, ScorePreviewService.GameOption> options =
                previewService.options(jwt, eventId).stream()
                        .collect(Collectors.toMap(
                                ScorePreviewService.GameOption::eventGameId,
                                option -> option
                        ));

        Set<UUID> selectedIds = new HashSet<>(selection.eventGameIds());

        if (!options.keySet().containsAll(selectedIds)) {
            throw badRequest(
                    "One of the selected activities does not belong to this event."
            );
        }

        List<SourceFixture> sourceFixtures = loadFixtures(eventId, scheduleId);
        List<GameView> games = new ArrayList<>();
        int byeCount = 0;

        for (UUID gameId : selection.eventGameIds().stream().sorted().toList()) {
            var option = options.get(gameId);

            if (option.unavailableReason() != null) {
                blockers.add(option.name() + ": " + option.unavailableReason());
                continue;
            }

            var version = versionRepository.findById(option.gameVersionId())
                    .orElseThrow(() ->
                            new EntityNotFoundException("Pinned rules not found.")
                    );

            var adapter = GameScoreAdapters.find(version.getScoringHandler());

            if (adapter == null) {
                blockers.add(option.name() + ": scoring adapter unavailable.");
                continue;
            }

            String expectedMode = adapter.headToHead()
                    ? "head_to_head" : "team_solo";

            if (!expectedMode.equals(version.getParticipationMode())) {
                blockers.add(
                        option.name() + ": the participation mode needs review."
                );
            }

            if (adapter.headToHead() && teams.size() < 2) {
                blockers.add(option.name() + " needs at least two active teams.");
            }

            Map<UUID, List<UUID>> appearances = new LinkedHashMap<>();
            teams.forEach(team -> appearances.put(team.id(), new ArrayList<>()));

            Set<String> pairs = new HashSet<>();
            List<FixtureView> fixtures = new ArrayList<>();

            for (SourceFixture fixture : sourceFixtures) {
                if (!fixture.gameId().equals(gameId)) continue;

                if (!"scheduled".equals(fixture.status())) {
                    blockers.add(
                            option.name()
                                    + ": resolve started or cancelled draft fixtures "
                                    + "before publication."
                    );
                }

                List<UUID> assigned = fixture.assignments().stream()
                        .map(Assignment::teamId)
                        .toList();

                if (assigned.isEmpty()
                        || !teamIds.containsAll(assigned)
                        || new HashSet<>(assigned).size() != assigned.size()) {
                    blockers.add(
                            option.name()
                                    + ": every fixture needs valid, active team assignments."
                    );
                    continue;
                }

                boolean bye = adapter.headToHead()
                        && assigned.size() == 1
                        && "bye".equals(fixture.assignments().getFirst().side());

                fixtures.add(new FixtureView(
                        fixture.id(),
                        fixture.round(),
                        fixture.startsAt(),
                        fixture.endsAt(),
                        assigned,
                        bye
                ));

                if (bye) {
                    byeCount++;
                    continue;
                }

                if (fixture.assignments().stream()
                        .anyMatch(assignment -> "bye".equals(assignment.side()))) {
                    blockers.add(
                            option.name()
                                    + ": a bye must contain exactly one team."
                    );
                    continue;
                }

                if (adapter.headToHead()) {
                    if (assigned.size() != 2) {
                        blockers.add(
                                option.name()
                                        + ": finish the opponent assignment "
                                        + "or explicitly mark a bye."
                        );
                        continue;
                    }

                    String pair = sortedIds(assigned);
                    if (!pairs.add(pair)) {
                        blockers.add(
                                option.name()
                                        + ": the same pair of teams is scheduled twice."
                        );
                    }
                }

                assigned.forEach(teamId ->
                        appearances.get(teamId).add(fixture.id())
                );
            }

            int expectedAppearances = adapter.headToHead()
                    ? Math.max(0, teams.size() - 1)
                    : 1;

            List<Opportunity> opportunities = new ArrayList<>();

            for (TeamView team : teams) {
                List<UUID> fixtureIds = appearances.get(team.id());

                if (fixtureIds.size() != expectedAppearances) {
                    blockers.add(
                            option.name() + " — " + team.name()
                                    + " has " + fixtureIds.size()
                                    + " appearance(s); expected "
                                    + expectedAppearances + "."
                    );
                }

                opportunities.add(new Opportunity(
                        team.id(),
                        fixtureIds.size(),
                        Math.multiplyExact(
                                fixtureIds.size(),
                                adapter.maximumPerFixture()
                        ),
                        List.copyOf(fixtureIds)
                ));
            }

            games.add(new GameView(
                    gameId,
                    version.getId(),
                    option.name(),
                    version.getVersion(),
                    version.getScoringHandler(),
                    ADAPTER_REVISION,
                    new LinkedHashMap<>(version.getScoringConfig()),
                    version.getRulesMarkdown(),
                    version.getSourceRef(),
                    adapter.decisionNote(),
                    adapter.maximumPerFixture(),
                    List.copyOf(fixtures),
                    List.copyOf(opportunities)
            ));
        }

        // Check actual play overlaps, including non-scoring activities.
        for (TeamView team : teams) {
            List<SourceFixture> playing = sourceFixtures.stream()
                    .filter(f -> "scheduled".equals(f.status()))
                    .filter(f -> f.assignments().stream().anyMatch(
                            a -> a.teamId().equals(team.id())
                                    && !"bye".equals(a.side())
                    ))
                    .sorted(Comparator.comparing(SourceFixture::startsAt))
                    .toList();

            Instant occupiedUntil = null;
            for (SourceFixture fixture : playing) {
                if (occupiedUntil != null
                        && fixture.startsAt().isBefore(occupiedUntil)) {
                    blockers.add(
                            team.name()
                                    + " has overlapping activities in this schedule."
                    );
                    break;
                }
                occupiedUntil = fixture.endsAt();
            }
        }

        List<String> excluded = options.values().stream()
                .filter(option -> !selectedIds.contains(option.eventGameId()))
                .map(ScorePreviewService.GameOption::name)
                .sorted()
                .toList();

        String reviewHash = sourceHash(eventId, scheduleId, selectedIds);

        return new Review(
                eventId,
                scheduleId,
                ((Number) schedule[0]).intValue(),
                "A",
                PRECISION,
                reviewHash,
                teams,
                List.copyOf(games),
                excluded,
                blockers.stream().distinct().toList(),
                games.size() * 5,
                byeCount
        );
    }

    private Event lockOrganizer(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        Event event = eventRepository.lockById(eventId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Event not found.")
                );

        membershipService.requireOrganizer(eventId, actorId);
        return event;
    }

    private void validateSelection(Selection selection) {
        if (selection == null
                || selection.scheduleId() == null
                || selection.eventGameIds() == null
                || selection.eventGameIds().isEmpty()
                || selection.eventGameIds().size() > 7
                || selection.eventGameIds().contains(null)
                || new HashSet<>(selection.eventGameIds()).size()
                != selection.eventGameIds().size()) {
            throw badRequest("Choose a schedule and distinct scored games.");
        }
    }

    private record Assignment(UUID teamId, String side) {
    }

    private record SourceFixture(
            UUID id,
            UUID gameId,
            int round,
            String status,
            Instant startsAt,
            Instant endsAt,
            List<Assignment> assignments
    ) {
    }

    private List<SourceFixture> loadFixtures(UUID eventId, UUID scheduleId) {
        List<Object[]> data = rows("""
                SELECT f.id, f.event_game_id, f.round_number, f.status,
                       to_char(s.start_at AT TIME ZONE 'UTC',
                               'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                       to_char(s.end_at AT TIME ZONE 'UTC',
                               'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                       ft.team_id, ft.side
                FROM murise.fixtures f
                JOIN murise.schedule_slots s
                  ON s.event_id = f.event_id AND s.id = f.slot_id
                LEFT JOIN murise.fixture_teams ft
                  ON ft.event_id = f.event_id AND ft.fixture_id = f.id
                WHERE f.event_id = :eventId
                  AND f.schedule_version_id = :scheduleId
                ORDER BY f.id, ft.team_id
                """, "eventId", eventId, "scheduleId", scheduleId);

        Map<UUID, List<Object[]>> grouped = new LinkedHashMap<>();
        for (Object[] row : data) {
            grouped.computeIfAbsent(uuid(row[0]), ignored -> new ArrayList<>())
                    .add(row);
        }

        List<SourceFixture> result = new ArrayList<>();

        for (var entry : grouped.entrySet()) {
            Object[] first = entry.getValue().getFirst();

            List<Assignment> assignments = entry.getValue().stream()
                    .filter(row -> row[6] != null)
                    .map(row -> new Assignment(uuid(row[6]), row[7].toString()))
                    .toList();

            result.add(new SourceFixture(
                    entry.getKey(),
                    uuid(first[1]),
                    ((Number) first[2]).intValue(),
                    first[3].toString(),
                    Instant.parse(first[4].toString()),
                    Instant.parse(first[5].toString()),
                    assignments
            ));
        }

        return result;
    }

    private String sourceHash(
            UUID eventId,
            UUID scheduleId,
            Set<UUID> selectedIds
    ) {
        String source = query("""
                SELECT CAST(jsonb_build_object(
                    'event', (
                        SELECT to_jsonb(e)
                        FROM murise.events e WHERE e.id = :eventId
                    ),
                    'schedule', (
                        SELECT to_jsonb(s)
                        FROM murise.schedule_versions s
                        WHERE s.event_id = :eventId AND s.id = :scheduleId
                    ),
                    'teams', (
                        SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                        FROM murise.teams t WHERE t.event_id = :eventId
                    ),
                    'lineup', (
                        SELECT jsonb_agg(to_jsonb(g) ORDER BY g.id)
                        FROM murise.event_games g WHERE g.event_id = :eventId
                    ),
                    'rules', (
                        SELECT jsonb_agg(to_jsonb(v) ORDER BY v.id)
                        FROM murise.game_versions v
                        JOIN murise.event_games g ON g.game_version_id = v.id
                        WHERE g.event_id = :eventId
                    ),
                    'fixtures', (
                        SELECT jsonb_agg(to_jsonb(f) ORDER BY f.id)
                        FROM murise.fixtures f
                        WHERE f.event_id = :eventId
                          AND f.schedule_version_id = :scheduleId
                    ),
                    'assignments', (
                        SELECT jsonb_agg(to_jsonb(ft)
                                        ORDER BY ft.fixture_id, ft.team_id)
                        FROM murise.fixture_teams ft
                        JOIN murise.fixtures f ON f.id = ft.fixture_id
                        WHERE f.event_id = :eventId
                          AND f.schedule_version_id = :scheduleId
                    ),
                    'slots', (
                        SELECT jsonb_agg(to_jsonb(s) ORDER BY s.id)
                        FROM murise.schedule_slots s
                        WHERE s.event_id = :eventId
                          AND s.schedule_version_id = :scheduleId
                    ),
                    'dependencies', (
                        SELECT jsonb_agg(to_jsonb(d)
                            ORDER BY d.source_fixture_id,
                                     d.target_fixture_id,
                                     d.target_side,
                                     d.qualification)
                        FROM murise.fixture_dependencies d
                        WHERE d.event_id = :eventId
                    )
                ) AS text)
                """, "eventId", eventId, "scheduleId", scheduleId)
                .getSingleResult().toString();

        return hash(ADAPTER_REVISION + "|" + sortedIds(selectedIds) + "|" + source);
    }

    private Map<String, Object> snapshot(Review review) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", review.eventId());
        result.put("scheduleId", review.scheduleId());
        result.put("scheduleVersion", review.scheduleVersion());
        result.put("policy", review.policy());
        result.put("precision", review.precision());
        result.put("reviewHash", review.reviewHash());
        result.put("teams", review.teams());
        result.put("games", review.games());
        result.put("excludedGames", review.excludedGames());
        result.put("blockers", review.blockers());
        result.put("maximumGamePoints", review.maximumGamePoints());
        result.put("byeCount", review.byeCount());
        return result;
    }

    private Published published(ScoringPlan plan) {
        return new Published(
                plan.getApprovedBy(),
                plan.getCreatedAt(),
                plan.getDecisionNote(),
                plan.getSnapshot()
        );
    }

    private Query query(String sql, Object... parameters) {
        Query query = entityManager.createNativeQuery(sql);
        for (int index = 0; index < parameters.length; index += 2) {
            query.setParameter(
                    parameters[index].toString(),
                    parameters[index + 1]
            );
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Object... parameters) {
        return (List<Object[]>) query(sql, parameters).getResultList();
    }

    private long count(String sql, Object... parameters) {
        return ((Number) query(sql, parameters).getSingleResult()).longValue();
    }

    private static UUID uuid(Object value) {
        return UUID.fromString(value.toString());
    }

    private static String sortedIds(Collection<UUID> values) {
        return values.stream().map(UUID::toString).sorted()
                .collect(Collectors.joining(","));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}