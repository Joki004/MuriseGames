package com.murisegames.api.modules.Event.Scoring.Settlement;

import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Referee.RefereeService;
import com.murisegames.api.modules.Event.Scoring.Adapter.ExactScore;
import com.murisegames.api.modules.Event.Scoring.Adapter.GameScoreAdapters;
import com.murisegames.api.modules.Event.Scoring.Plan.ScoringPlan;
import com.murisegames.api.modules.Event.Scoring.Plan.ScoringPlanRepository;
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
import java.util.*;

import static com.murisegames.api.modules.Event.Scoring.Settlement.OfficialResultTypes.*;

@Service
@RequiredArgsConstructor
public class OfficialResultService {

    private final ScoringPlanRepository planRepository;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final RefereeService refereeService;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional
    public FixtureSettlementResponse recordFixtureResult(
            Jwt jwt,
            UUID eventId,
            UUID fixtureId,
            RecordFixtureResultRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        // Organizers, or a referee assigned to this game (checked below once the
        // fixture's game and teams are known).
        Set<String> roles = membershipService.requireAccess(eventId, actorId);
        if (!roles.contains(RefereeService.ROLE_ORGANIZER)
                && !roles.contains(RefereeService.ROLE_REFEREE)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Organizer or assigned referee permission required.");
        }
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        ScoringPlan plan = planRepository.findById(eventId)
                .orElseThrow(() -> conflict("Publish a scoring plan before recording official results."));

        PlannedFixture planned = findPlannedFixture(plan.getSnapshot(), fixtureId);
        refereeService.requireCanRecordResult(
                eventId, actorId, planned.eventGameId(), planned.teamIds());
        if (planned.bye()) {
            throw badRequest("Bye fixtures do not accept scores.");
        }
        String reason = requireReason(request.reason());

        if ("voided".equals(loadFixtureStatus(eventId, fixtureId))) {
            throw conflict("This fixture was cancelled and cannot accept scores.");
        }

        GameScoreAdapters.Definition adapter = GameScoreAdapters.find(planned.scoringHandler());
        if (adapter == null) {
            throw conflict(GameScoreAdapters.unavailableReason(planned.scoringHandler()));
        }

        validateTeamValues(planned, adapter, request.teamValues());

        Map<UUID, Integer> rawByTeam = new LinkedHashMap<>();
        for (UUID teamId : planned.teamIds()) {
            int sum = request.teamValues().get(teamId).stream().mapToInt(Integer::intValue).sum();
            rawByTeam.put(teamId, sum);
        }

        for (UUID teamId : planned.teamIds()) {
            int rawScore = rawByTeam.get(teamId);
            String outcome = resolveOutcome(adapter.headToHead(), teamId, rawByTeam);
            String evidenceJson = "{\"values\":" + request.teamValues().get(teamId) + "}";

            entityManager.createNativeQuery("""
                    INSERT INTO murise.fixture_results (
                        event_id, fixture_id, team_id, raw_score, raw_max, outcome, evidence
                    )
                    VALUES (
                        :eventId, :fixtureId, :teamId, :rawScore, :rawMax, :outcome, CAST(:evidence AS jsonb)
                    )
                    ON CONFLICT (fixture_id, team_id)
                    DO UPDATE SET
                        raw_score = EXCLUDED.raw_score,
                        raw_max   = EXCLUDED.raw_max,
                        outcome   = EXCLUDED.outcome,
                        evidence  = EXCLUDED.evidence,
                        revision  = murise.fixture_results.revision + 1
                    """)
                    .setParameter("eventId", eventId)
                    .setParameter("fixtureId", fixtureId)
                    .setParameter("teamId", teamId)
                    .setParameter("rawScore", BigDecimal.valueOf(rawScore))
                    .setParameter("rawMax", BigDecimal.valueOf(planned.rawMaximumPerFixture()))
                    .setParameter("outcome", outcome)
                    .setParameter("evidence", evidenceJson)
                    .executeUpdate();
        }

        entityManager.createNativeQuery("""
                UPDATE murise.fixtures
                SET status = 'completed'
                WHERE event_id = :eventId AND id = :fixtureId
                """)
                .setParameter("eventId", eventId)
                .setParameter("fixtureId", fixtureId)
                .executeUpdate();

        List<TeamSettlement> settlements = new ArrayList<>();

        for (UUID teamId : planned.teamIds()) {
            Integer denominator = planned.denominatorsByTeam().get(teamId);
            if (denominator == null || denominator <= 0) {
                throw conflict("The scoring plan has no valid denominator for a team in this fixture.");
            }
            int frozenDenominator = denominator;
            List<UUID> teamFixtureIds = planned.fixtureIdsByTeam().getOrDefault(teamId, List.of());

            int cumulativeRaw = sumConfirmedRaw(eventId, teamId, teamFixtureIds);
            ExactScore newExact = ExactScore.of(5L * cumulativeRaw, frozenDenominator);
            BigDecimal newSettled = newExact.settlementAmount();

            GameResultRow previous = loadGameResult(eventId, planned.eventGameId(), teamId);
            BigDecimal previousSettled = previous != null ? previous.normalizedPoints() : BigDecimal.ZERO;
            BigDecimal delta = newSettled.subtract(previousSettled);

            int nextRevision = previous != null ? previous.revision() + 1 : 1;
            UUID gameResultId = upsertGameResult(
                    eventId,
                    planned.eventGameId(),
                    teamId,
                    cumulativeRaw,
                    frozenDenominator,
                    newSettled,
                    nextRevision
            );

            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                String sourceKey = "settlement:" + planned.eventGameId() + ":" + teamId + ":rev:" + nextRevision;
                entityManager.createNativeQuery("""
                        INSERT INTO murise.score_entries (
                            event_id, team_id, result_id, category, delta, source_key, reason, actor_id
                        )
                        VALUES (
                            :eventId, :teamId, :resultId, 'game_points', :delta, :sourceKey, :reason, :actorId
                        )
                        """)
                        .setParameter("eventId", eventId)
                        .setParameter("teamId", teamId)
                        .setParameter("resultId", gameResultId)
                        .setParameter("delta", delta)
                        .setParameter("sourceKey", sourceKey)
                        .setParameter("reason", reason)
                        .setParameter("actorId", actorId)
                        .executeUpdate();
            }

            settlements.add(new TeamSettlement(
                    teamId,
                    rawByTeam.get(teamId),
                    cumulativeRaw,
                    frozenDenominator,
                    newSettled,
                    delta,
                    newExact.display(),
                    nextRevision
            ));
        }

        recordAudit(eventId, actorId, "FIXTURE_RESULT_RECORDED", fixtureId, reason);

        return new FixtureSettlementResponse(
                eventId,
                planned.eventGameId(),
                fixtureId,
                "completed",
                List.copyOf(settlements)
        );
    }

    @Transactional
    public void cancelFixture(
            Jwt jwt,
            UUID eventId,
            UUID fixtureId,
            CancelFixtureRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);
        eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));

        ScoringPlan plan = planRepository.findById(eventId)
                .orElseThrow(() -> conflict("No published scoring plan for this event."));

        findPlannedFixture(plan.getSnapshot(), fixtureId);
        String reason = requireReason(request.reason());

        String status = loadFixtureStatus(eventId, fixtureId);
        if ("completed".equals(status)) {
            throw conflict("This fixture already has an official result; correct the result instead of cancelling it.");
        }
        if ("voided".equals(status)) {
            return;
        }

        entityManager.createNativeQuery("""
                UPDATE murise.fixtures
                SET status = 'voided'
                WHERE event_id = :eventId AND id = :fixtureId
                """)
                .setParameter("eventId", eventId)
                .setParameter("fixtureId", fixtureId)
                .executeUpdate();

        recordAudit(eventId, actorId, "FIXTURE_CANCELLED", fixtureId, reason);
    }

    @Transactional(readOnly = true)
    public RankingStatusResponse getRankingStatus(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, actorId);

        ScoringPlan plan = planRepository.findById(eventId)
                .orElseThrow(() -> conflict("No published scoring plan for this event."));

        Map<String, Object> snap = plan.getSnapshot();
        List<Map<String, Object>> teamMaps = listOfMaps(snap.get("teams"));
        List<Map<String, Object>> gameMaps = listOfMaps(snap.get("games"));

        Map<UUID, String> fixtureStatuses = loadFixtureStatuses(eventId, plan.getScheduleId());
        Map<String, Integer> rawByFixtureTeam = loadAllFixtureRawScores(eventId);

        List<String> blockers = new ArrayList<>();
        List<String> includedGames = new ArrayList<>();
        Map<UUID, ExactScore> exactTotals = new LinkedHashMap<>();
        Map<UUID, String> teamNames = new LinkedHashMap<>();

        for (var t : teamMaps) {
            UUID id = UUID.fromString(t.get("id").toString());
            teamNames.put(id, t.get("name").toString());
            exactTotals.put(id, ExactScore.ZERO);
        }

        for (var g : gameMaps) {
            String gameName = g.get("name").toString();
            includedGames.add(gameName);

            for (var f : listOfMaps(g.get("fixtures"))) {
                if (Boolean.TRUE.equals(f.get("bye"))) continue;
                UUID fid = UUID.fromString(f.get("id").toString());
                String status = fixtureStatuses.getOrDefault(fid, "scheduled");
                if ("voided".equals(status)) {
                    blockers.add(gameName + ": a planned fixture was cancelled; denominator remains frozen.");
                } else if (!"completed".equals(status)) {
                    blockers.add(gameName + ": one or more planned fixtures have not been completed.");
                }
            }

            for (var opp : listOfMaps(g.get("opportunities"))) {
                UUID teamId = UUID.fromString(opp.get("teamId").toString());
                int denominator = ((Number) opp.get("rawDenominator")).intValue();
                if (denominator <= 0) continue;

                int rawSum = 0;
                for (Object rawFid : (List<?>) opp.get("fixtureIds")) {
                    rawSum += rawByFixtureTeam.getOrDefault(rawFid + ":" + teamId, 0);
                }
                ExactScore gameContribution = ExactScore.of(5L * rawSum, denominator);
                exactTotals.merge(teamId, gameContribution, ExactScore::add);
            }
        }

        List<RankedTeam> standings = rankExactTotals(teamNames, exactTotals);
        int maxPoints = ((Number) snap.getOrDefault("maximumGamePoints", includedGames.size() * 5)).intValue();

        return new RankingStatusResponse(
                eventId,
                String.valueOf(snap.getOrDefault("policy", "A")),
                maxPoints,
                blockers.isEmpty(),
                blockers.stream().distinct().toList(),
                List.copyOf(includedGames),
                standings
        );
    }

    static List<RankedTeam> rankExactTotals(
            Map<UUID, String> teamNames,
            Map<UUID, ExactScore> exactTotals
    ) {
        List<UUID> sorted = new ArrayList<>(teamNames.keySet());
        sorted.sort(Comparator
                .comparing((UUID id) -> exactTotals.getOrDefault(id, ExactScore.ZERO))
                .reversed()
                .thenComparing(id -> teamNames.getOrDefault(id, ""), String.CASE_INSENSITIVE_ORDER));

        List<RankedTeam> result = new ArrayList<>();
        ExactScore previous = null;
        int rank = 0;

        for (int i = 0; i < sorted.size(); i++) {
            UUID teamId = sorted.get(i);
            ExactScore score = exactTotals.getOrDefault(teamId, ExactScore.ZERO);
            if (previous == null || score.compareTo(previous) != 0) {
                rank = i + 1;
                previous = score;
            }
            result.add(new RankedTeam(
                    rank,
                    teamId,
                    teamNames.get(teamId),
                    score.numerator().toString(),
                    score.denominator().toString(),
                    score.display(),
                    score.settlementAmount()
            ));
        }
        return result;
    }

    private void validateTeamValues(
            PlannedFixture planned,
            GameScoreAdapters.Definition adapter,
            Map<UUID, List<Integer>> teamValues
    ) {
        Set<UUID> expected = new HashSet<>(planned.teamIds());
        if (teamValues == null || !expected.equals(teamValues.keySet())) {
            throw badRequest("Provide scores for every team assigned to this fixture.");
        }

        for (UUID teamId : planned.teamIds()) {
            List<Integer> values = teamValues.get(teamId);
            if (values == null || values.size() != adapter.fields().size()) {
                throw badRequest("Complete every scoring field for all teams in this fixture.");
            }
            for (Integer v : values) {
                if (v == null || v < 0 || v > adapter.maximumPerField()) {
                    throw badRequest("Values must be whole numbers between 0 and " + adapter.maximumPerField() + ".");
                }
            }
        }

        if (adapter.headToHead() && planned.teamIds().size() == 2) {
            List<Integer> a = teamValues.get(planned.teamIds().get(0));
            List<Integer> b = teamValues.get(planned.teamIds().get(1));
            for (int i = 0; i < adapter.fields().size(); i++) {
                if (a.get(i) + b.get(i) != adapter.maximumPerField()) {
                    throw badRequest(
                            adapter.fields().get(i) + ": opposing team values must total " + adapter.maximumPerField() + "."
                    );
                }
            }
        }
    }

    private String resolveOutcome(boolean headToHead, UUID teamId, Map<UUID, Integer> rawByTeam) {
        if (!headToHead || rawByTeam.size() != 2) return "placed";
        int mine = rawByTeam.get(teamId);
        int other = rawByTeam.entrySet().stream()
                .filter(e -> !e.getKey().equals(teamId))
                .findFirst().orElseThrow().getValue();
        if (mine > other) return "win";
        if (mine < other) return "loss";
        return "draw";
    }

    private int sumConfirmedRaw(UUID eventId, UUID teamId, List<UUID> fixtureIds) {
        if (fixtureIds.isEmpty()) return 0;
        Object result = entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(raw_score), 0)
                FROM murise.fixture_results
                WHERE event_id = :eventId
                  AND team_id = :teamId
                  AND fixture_id IN (:fixtureIds)
                """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", teamId)
                .setParameter("fixtureIds", fixtureIds)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    private GameResultRow loadGameResult(UUID eventId, UUID eventGameId, UUID teamId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, normalized_points, revision
                FROM murise.game_results
                WHERE event_id = :eventId AND event_game_id = :eventGameId AND team_id = :teamId
                """)
                .setParameter("eventId", eventId)
                .setParameter("eventGameId", eventGameId)
                .setParameter("teamId", teamId)
                .getResultList();
        if (rows.isEmpty()) return null;
        Object[] r = rows.getFirst();
        return new GameResultRow(
                UUID.fromString(r[0].toString()),
                new BigDecimal(r[1].toString()),
                ((Number) r[2]).intValue()
        );
    }

    private UUID upsertGameResult(
            UUID eventId,
            UUID eventGameId,
            UUID teamId,
            int rawScore,
            int rawMax,
            BigDecimal normalizedPoints,
            int revision
    ) {
        Object id = entityManager.createNativeQuery("""
                INSERT INTO murise.game_results (
                    event_id, event_game_id, team_id, raw_score, raw_max, normalized_points, status, revision
                )
                VALUES (
                    :eventId, :eventGameId, :teamId, :rawScore, :rawMax, :normalizedPoints, 'provisional', :revision
                )
                ON CONFLICT (event_game_id, team_id)
                DO UPDATE SET
                    raw_score         = EXCLUDED.raw_score,
                    raw_max           = EXCLUDED.raw_max,
                    normalized_points = EXCLUDED.normalized_points,
                    revision          = EXCLUDED.revision
                RETURNING id
                """)
                .setParameter("eventId", eventId)
                .setParameter("eventGameId", eventGameId)
                .setParameter("teamId", teamId)
                .setParameter("rawScore", BigDecimal.valueOf(rawScore))
                .setParameter("rawMax", BigDecimal.valueOf(rawMax))
                .setParameter("normalizedPoints", normalizedPoints)
                .setParameter("revision", revision)
                .getSingleResult();
        return UUID.fromString(id.toString());
    }

    private Map<UUID, String> loadFixtureStatuses(UUID eventId, UUID scheduleId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, status
                FROM murise.fixtures
                WHERE event_id = :eventId AND schedule_version_id = :scheduleId
                """)
                .setParameter("eventId", eventId)
                .setParameter("scheduleId", scheduleId)
                .getResultList();
        Map<UUID, String> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put(UUID.fromString(r[0].toString()), r[1].toString());
        }
        return map;
    }

    private Map<String, Integer> loadAllFixtureRawScores(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT fixture_id, team_id, raw_score
                FROM murise.fixture_results
                WHERE event_id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();
        Map<String, Integer> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put(r[0].toString() + ":" + r[1].toString(), ((Number) r[2]).intValue());
        }
        return map;
    }

    private void recordAudit(UUID eventId, UUID actorId, String action, UUID entityId, String reason) {
        entityManager.createNativeQuery("""
                INSERT INTO murise.audit_events (
                    event_id, actor_id, action, entity_type, entity_id, details
                )
                VALUES (
                    :eventId, :actorId, :action, 'fixture', :entityId,
                    jsonb_build_object('reason', CAST(:reason AS text))
                )
                """)
                .setParameter("eventId", eventId)
                .setParameter("actorId", actorId)
                .setParameter("action", action)
                .setParameter("entityId", entityId)
                .setParameter("reason", reason)
                .executeUpdate();
    }

    private PlannedFixture findPlannedFixture(Map<String, Object> snapshot, UUID fixtureId) {
        for (var g : listOfMaps(snapshot.get("games"))) {
            UUID eventGameId = UUID.fromString(g.get("eventGameId").toString());
            String handler = g.get("scoringHandler").toString();
            int rawMaxPerFixture = ((Number) g.get("rawMaximumPerFixture")).intValue();

            Map<UUID, Integer> denominators = new HashMap<>();
            Map<UUID, List<UUID>> fixtureIdsByTeam = new HashMap<>();
            for (var opp : listOfMaps(g.get("opportunities"))) {
                UUID tid = UUID.fromString(opp.get("teamId").toString());
                denominators.put(tid, ((Number) opp.get("rawDenominator")).intValue());
                List<UUID> fids = ((List<?>) opp.get("fixtureIds")).stream()
                        .map(o -> UUID.fromString(o.toString())).toList();
                fixtureIdsByTeam.put(tid, fids);
            }

            for (var f : listOfMaps(g.get("fixtures"))) {
                UUID fid = UUID.fromString(f.get("id").toString());
                if (fid.equals(fixtureId)) {
                    List<UUID> teamIds = ((List<?>) f.get("teamIds")).stream()
                            .map(o -> UUID.fromString(o.toString())).toList();
                    return new PlannedFixture(
                            eventGameId,
                            handler,
                            rawMaxPerFixture,
                            teamIds,
                            Boolean.TRUE.equals(f.get("bye")),
                            denominators,
                            fixtureIdsByTeam
                    );
                }
            }
        }
        throw new EntityNotFoundException("Fixture not found in the published scoring plan.");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (value == null) return List.of();
        return (List<Map<String, Object>>) value;
    }

    private String loadFixtureStatus(UUID eventId, UUID fixtureId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT status
                FROM murise.fixtures
                WHERE event_id = :eventId AND id = :fixtureId
                """)
                .setParameter("eventId", eventId)
                .setParameter("fixtureId", fixtureId)
                .getResultList();
        if (rows.isEmpty()) {
            throw new EntityNotFoundException("Fixture not found.");
        }
        return rows.getFirst().toString();
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw badRequest("A reason is required.");
        }
        return reason.trim();
    }

    private record PlannedFixture(
            UUID eventGameId,
            String scoringHandler,
            int rawMaximumPerFixture,
            List<UUID> teamIds,
            boolean bye,
            Map<UUID, Integer> denominatorsByTeam,
            Map<UUID, List<UUID>> fixtureIdsByTeam
    ) {
    }

    private record GameResultRow(UUID id, BigDecimal normalizedPoints, int revision) {
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}