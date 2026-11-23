package com.murisegames.api.modules.Event.Scoring.TeamResults;

import com.murisegames.api.modules.Audit.AuditService;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Participant.EventParticipantRepository;
import com.murisegames.api.modules.Event.Participant.EventParticipantService;
import com.murisegames.api.modules.Event.Scoring.Adapter.GameScoreAdapters;
import com.murisegames.api.modules.Event.Scoring.ScoringService;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.murisegames.api.modules.Event.Scoring.TeamResults.TeamResultsTypes.*;

/**
 * What a team sees about its own results on the Player screen. The host picks
 * the mode; the team's own match results are always visible to its members,
 * so they can raise a wrong result with the host straight away.
 */
@Service
@RequiredArgsConstructor
public class TeamResultsService {

    private final EventMembershipService membershipService;
    private final EventParticipantRepository participantRepository;
    private final TeamRepository teamRepository;
    private final ScoringService scoringService;
    private final UserService userService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    // =====================================================================
    // Organizer setting
    // =====================================================================

    @Transactional(readOnly = true)
    public SettingResponse setting(Jwt jwt, UUID eventId) {
        membershipService.requireOrganizer(eventId, userService.extractUserId(jwt));
        return new SettingResponse(readMode(eventId));
    }

    @Transactional
    public SettingResponse updateSetting(Jwt jwt, UUID eventId, SettingRequest request) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        String previous = readMode(eventId);
        if (!previous.equals(request.mode())) {
            entityManager.createNativeQuery("""
                    UPDATE murise.events
                    SET team_results_visibility = :mode
                    WHERE id = :eventId
                    """)
                    .setParameter("mode", request.mode())
                    .setParameter("eventId", eventId)
                    .executeUpdate();

            auditService.record(
                    eventId, actorId, "team_results.visibility_changed", "event", eventId,
                    Map.of("from", previous, "to", request.mode())
            );
        }
        return new SettingResponse(request.mode());
    }

    // =====================================================================
    // Player: my team's results
    // =====================================================================

    @Transactional(readOnly = true)
    public MyResultsResponse myResults(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, userId);
        String mode = readMode(eventId);

        UUID teamId = participantRepository.findByEventIdAndUserId(eventId, userId)
                .filter(p -> EventParticipantService.STATUS_ACTIVE.equals(p.getStatus()))
                .map(p -> p.getTeamId())
                .orElse(null);
        Team team = teamId == null ? null : teamRepository.findByIdAndEventId(teamId, eventId)
                .orElse(null);

        boolean showPoints = OWN_POINTS.equals(mode) || FULL_SCOREBOARD.equals(mode);
        var scoreboard = FULL_SCOREBOARD.equals(mode)
                ? scoringService.liveStandingsUnchecked(eventId)
                : null;

        if (team == null) {
            return new MyResultsResponse(mode, null, null, null, List.of(),
                    null, null, null, scoreboard);
        }

        List<MatchView> matches = loadMatches(eventId, team.getId());

        List<GamePoints> gamePoints = null;
        BigDecimal otherPoints = null;
        BigDecimal totalPoints = null;
        if (showPoints) {
            gamePoints = loadGamePoints(eventId, team.getId());
            otherPoints = loadOtherPoints(eventId, team.getId());
            totalPoints = gamePoints.stream()
                    .map(GamePoints::points)
                    .reduce(otherPoints, BigDecimal::add);
        }

        return new MyResultsResponse(mode, team.getId(), team.getName(), team.getColor(),
                matches, gamePoints, otherPoints, totalPoints, scoreboard);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private List<MatchView> loadMatches(UUID eventId, UUID teamId) {
        @SuppressWarnings("unchecked")
        List<Object[]> fixtureRows = entityManager.createNativeQuery("""
                SELECT f.id, gd.name, f.round_number, s.start_at, f.status, gv.scoring_handler
                FROM murise.fixtures f
                JOIN murise.schedule_versions sv
                  ON sv.id = f.schedule_version_id AND sv.status = 'published'
                JOIN murise.schedule_slots s ON s.id = f.slot_id
                JOIN murise.event_games eg ON eg.id = f.event_game_id
                JOIN murise.game_definitions gd ON gd.id = eg.game_definition_id
                JOIN murise.game_versions gv ON gv.id = eg.game_version_id
                WHERE f.event_id = :eventId
                  AND EXISTS (
                      SELECT 1 FROM murise.fixture_teams ft
                      WHERE ft.fixture_id = f.id AND ft.team_id = :teamId AND ft.side <> 'bye'
                  )
                ORDER BY s.start_at, gd.name, f.round_number
                """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", teamId)
                .getResultList();

        if (fixtureRows.isEmpty()) {
            return List.of();
        }
        List<UUID> fixtureIds = fixtureRows.stream().map(r -> uuid(r[0])).toList();

        // Every team in those fixtures, with its recorded result if there is one.
        @SuppressWarnings("unchecked")
        List<Object[]> teamRows = entityManager.createNativeQuery("""
                SELECT ft.fixture_id, ft.team_id, t.name,
                       fr.raw_score, fr.raw_max, fr.outcome, CAST(fr.evidence AS text),
                       fr.revision, fr.updated_at
                FROM murise.fixture_teams ft
                JOIN murise.teams t ON t.id = ft.team_id
                LEFT JOIN murise.fixture_results fr
                  ON fr.fixture_id = ft.fixture_id AND fr.team_id = ft.team_id
                WHERE ft.event_id = :eventId
                  AND ft.fixture_id IN (:fixtureIds)
                  AND ft.side <> 'bye'
                ORDER BY t.sort_order, t.name
                """)
                .setParameter("eventId", eventId)
                .setParameter("fixtureIds", fixtureIds)
                .getResultList();

        Map<UUID, List<Object[]>> teamsByFixture = new HashMap<>();
        for (Object[] r : teamRows) {
            teamsByFixture.computeIfAbsent(uuid(r[0]), k -> new ArrayList<>()).add(r);
        }

        List<MatchView> matches = new ArrayList<>();
        for (Object[] f : fixtureRows) {
            UUID fixtureId = uuid(f[0]);
            var adapter = GameScoreAdapters.find(f[5].toString());
            List<String> fieldLabels = adapter != null ? adapter.fields() : List.of();

            Object[] mine = null;
            List<OpponentView> opponents = new ArrayList<>();
            for (Object[] t : teamsByFixture.getOrDefault(fixtureId, List.of())) {
                if (uuid(t[1]).equals(teamId)) {
                    mine = t;
                } else {
                    opponents.add(new OpponentView(
                            uuid(t[1]), t[2].toString(), decimal(t[3]),
                            parseValues(t[6]), t[5] != null ? t[5].toString() : null
                    ));
                }
            }

            boolean recorded = mine != null && mine[3] != null;
            matches.add(new MatchView(
                    fixtureId,
                    f[1].toString(),
                    ((Number) f[2]).intValue(),
                    toInstant(f[3]),
                    f[4].toString(),
                    recorded ? mine[5].toString() : null,
                    recorded ? decimal(mine[3]) : null,
                    recorded ? decimal(mine[4]) : null,
                    fieldLabels,
                    recorded ? parseValues(mine[6]) : List.of(),
                    opponents,
                    recorded && ((Number) mine[7]).intValue() > 1,
                    recorded ? toInstant(mine[8]) : null
            ));
        }
        return matches;
    }

    private List<GamePoints> loadGamePoints(UUID eventId, UUID teamId) {
        // Ledger entries linked to a game result, reversals included (they keep result_id).
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT gr.event_game_id, gd.name, COALESCE(SUM(se.delta), 0)
                FROM murise.score_entries se
                JOIN murise.game_results gr ON gr.id = se.result_id
                JOIN murise.event_games eg ON eg.id = gr.event_game_id
                JOIN murise.game_definitions gd ON gd.id = eg.game_definition_id
                WHERE se.event_id = :eventId AND se.team_id = :teamId
                GROUP BY gr.event_game_id, gd.name, eg.sort_order
                ORDER BY eg.sort_order, gd.name
                """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", teamId)
                .getResultList();

        return rows.stream()
                .map(r -> new GamePoints(uuid(r[0]), r[1].toString(), decimal(r[2])))
                .toList();
    }

    /** Secret Scene points, host bonuses/penalties and manual corrections. */
    private BigDecimal loadOtherPoints(UUID eventId, UUID teamId) {
        Object value = entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(delta), 0)
                FROM murise.score_entries
                WHERE event_id = :eventId AND team_id = :teamId AND result_id IS NULL
                """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", teamId)
                .getSingleResult();
        return decimal(value);
    }

    private String readMode(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT team_results_visibility FROM murise.events WHERE id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getResultList();
        return rows.isEmpty() ? OWN_RESULTS : rows.getFirst().toString();
    }

    private static final Pattern NUMBER = Pattern.compile("-?\\d+");

    /** Evidence is stored as {"values": [1, 0, 1, 1]}. */
    static List<Integer> parseValues(Object evidence) {
        if (evidence == null) return List.of();
        String json = evidence.toString();
        int open = json.indexOf('[');
        int close = json.indexOf(']', open + 1);
        if (open < 0 || close < 0) return List.of();

        List<Integer> values = new ArrayList<>();
        Matcher m = NUMBER.matcher(json.substring(open + 1, close));
        while (m.find()) {
            values.add(Integer.parseInt(m.group()));
        }
        return List.copyOf(values);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        return value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID u ? u : UUID.fromString(value.toString());
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(value.toString());
    }
}
