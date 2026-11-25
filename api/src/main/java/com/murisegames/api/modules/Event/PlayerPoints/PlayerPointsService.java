package com.murisegames.api.modules.Event.PlayerPoints;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.Credit;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.FixtureCreditsResponse;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.FixturePlayer;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.FixtureTeamPlayers;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.LiveAward;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.RankingResponse;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.RankingRow;
import com.murisegames.api.modules.Event.Referee.RefereeService;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlayerPointsService {

    private final UserService users;
    private final EventMembershipService memberships;
    private final RefereeService refereeService;
    private final EntityManager entityManager;

    // =====================================================================
    // Referee: credit players for a match
    // =====================================================================

    @Transactional(readOnly = true)
    public FixtureCreditsResponse fixtureCredits(Jwt jwt, UUID eventId, UUID fixtureId) {
        UUID actorId = users.extractUserId(jwt);
        FixtureInfo fixture = loadFixture(eventId, fixtureId, false);
        requireCanCredit(eventId, actorId, fixture);
        return view(eventId, fixture);
    }

    @Transactional
    public FixtureCreditsResponse saveFixtureCredits(
            Jwt jwt,
            UUID eventId,
            UUID fixtureId,
            List<Credit> requested
    ) {
        UUID actorId = users.extractUserId(jwt);
        // The row lock serializes two referees saving the same match.
        FixtureInfo fixture = loadFixture(eventId, fixtureId, true);
        requireCanCredit(eventId, actorId, fixture);

        if ("voided".equals(fixture.status())) {
            throw conflict("This match was cancelled; players can't be credited.");
        }

        List<String> segments = PlayerPointsRules.segmentsFor(fixture.scoringHandler());
        if (segments.isEmpty()) {
            throw conflict("This activity has no star players.");
        }

        List<FixtureTeamPlayers> teams = loadPlayers(eventId, fixture.playingTeamIds());
        Map<UUID, UUID> teamOfParticipant = new LinkedHashMap<>();
        teams.forEach(team -> team.players().forEach(player ->
                teamOfParticipant.put(player.participantId(), team.teamId())));

        List<Credit> credits;
        try {
            credits = PlayerPointsRules.validate(
                    requested, segments.size(), teamOfParticipant.keySet());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        PlayerPointsRules.Diff diff = PlayerPointsRules.diff(
                loadLiveAwards(eventId, fixtureId), credits);

        if (!diff.isEmpty()) {
            for (LiveAward award : diff.toReverse()) {
                insertReversal(eventId, actorId, award.id());
            }

            for (Credit credit : diff.toAward()) {
                insertAward(
                        eventId,
                        fixtureId,
                        credit,
                        segments.get(credit.segmentIndex()),
                        teamOfParticipant.get(credit.participantId()),
                        actorId
                );
            }

            recordAudit(eventId, actorId, fixtureId, diff);
        }

        return view(eventId, fixture);
    }

    // =====================================================================
    // Everyone: the Star players ranking
    // =====================================================================

    /** Follows the team scoreboard: hidden for players when it is hidden, cut at the freeze. */
    @Transactional(readOnly = true)
    public RankingResponse ranking(Jwt jwt, UUID eventId) {
        UUID actorId = users.extractUserId(jwt);
        Set<String> roles = memberships.requireAccess(eventId, actorId);
        boolean organizer = roles.contains(RefereeService.ROLE_ORGANIZER);

        Object[] state = (Object[]) entityManager.createNativeQuery("""
                        SELECT e.scoreboard_visibility, s.created_at
                        FROM murise.events e
                        LEFT JOIN murise.scoreboard_snapshots s
                          ON s.event_id = e.id AND s.id = e.frozen_snapshot_id
                        WHERE e.id = :eventId
                        """)
                .setParameter("eventId", eventId)
                .getSingleResult();

        String visibility = (String) state[0];
        Instant frozenAt = "frozen".equals(visibility) ? instant(state[1]) : null;

        if ("hidden".equals(visibility) && !organizer) {
            return new RankingResponse(eventId, visibility, true, null, List.of());
        }

        String cutoff = frozenAt == null ? "" : " AND pp.created_at <= :frozenAt";

        var query = entityManager.createNativeQuery("""
                SELECT pp.participant_id, ep.user_id, ep.display_name,
                       ep.team_id, t.name, t.color, SUM(pp.points) AS total
                FROM murise.player_points pp
                JOIN murise.event_participants ep
                  ON ep.event_id = pp.event_id AND ep.id = pp.participant_id
                LEFT JOIN murise.teams t
                  ON t.event_id = ep.event_id AND t.id = ep.team_id
                WHERE pp.event_id = :eventId
                """ + cutoff + """

                GROUP BY pp.participant_id, ep.user_id, ep.display_name,
                         ep.team_id, t.name, t.color
                HAVING SUM(pp.points) > 0
                ORDER BY total DESC, ep.display_name ASC
                """)
                .setParameter("eventId", eventId);

        if (frozenAt != null) {
            query.setParameter("frozenAt", OffsetDateTime.ofInstant(frozenAt, java.time.ZoneOffset.UTC));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        int[] ranks = PlayerPointsRules.ranks(rows, r -> ((Number) r[6]).intValue());

        List<RankingRow> ranking = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            ranking.add(new RankingRow(
                    uuid(r[0]),
                    uuid(r[1]),
                    (String) r[2],
                    uuid(r[3]),
                    r[4] == null ? "No team" : (String) r[4],
                    r[5] == null ? "#64748b" : (String) r[5],
                    ((Number) r[6]).intValue(),
                    ranks[i]
            ));
        }

        return new RankingResponse(eventId, visibility, false, frozenAt, List.copyOf(ranking));
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private record FixtureInfo(
            UUID fixtureId,
            UUID eventGameId,
            String status,
            int roundNumber,
            String gameName,
            String scoringHandler,
            List<UUID> allTeamIds,
            List<UUID> playingTeamIds
    ) {
    }

    private void requireCanCredit(UUID eventId, UUID actorId, FixtureInfo fixture) {
        Set<String> roles = memberships.requireAccess(eventId, actorId);
        if (!roles.contains(RefereeService.ROLE_ORGANIZER)
                && !roles.contains(RefereeService.ROLE_REFEREE)) {
            throw new AccessDeniedException("Organizer or assigned referee permission required.");
        }
        // Same rule as recording the result: assigned game, never their own team's match.
        refereeService.requireCanRecordResult(
                eventId, actorId, fixture.eventGameId(), fixture.allTeamIds());
    }

    private FixtureInfo loadFixture(UUID eventId, UUID fixtureId, boolean lock) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT f.event_game_id, f.status, f.round_number
                        FROM murise.fixtures f
                        WHERE f.event_id = :eventId AND f.id = :fixtureId
                        """ + (lock ? " FOR UPDATE" : ""))
                .setParameter("eventId", eventId)
                .setParameter("fixtureId", fixtureId)
                .getResultList();

        if (rows.isEmpty()) {
            throw new EntityNotFoundException("Match not found.");
        }

        UUID eventGameId = uuid(rows.getFirst()[0]);
        String status = (String) rows.getFirst()[1];
        int roundNumber = ((Number) rows.getFirst()[2]).intValue();

        Object[] game = (Object[]) entityManager.createNativeQuery("""
                        SELECT gd.name, gv.scoring_handler
                        FROM murise.event_games eg
                        JOIN murise.game_versions gv ON gv.id = eg.game_version_id
                        JOIN murise.game_definitions gd ON gd.id = eg.game_definition_id
                        WHERE eg.event_id = :eventId AND eg.id = :eventGameId
                        """)
                .setParameter("eventId", eventId)
                .setParameter("eventGameId", eventGameId)
                .getSingleResult();

        @SuppressWarnings("unchecked")
        List<Object[]> teamRows = entityManager.createNativeQuery("""
                        SELECT ft.team_id, ft.side
                        FROM murise.fixture_teams ft
                        WHERE ft.event_id = :eventId AND ft.fixture_id = :fixtureId
                        ORDER BY ft.created_at, ft.team_id
                        """)
                .setParameter("eventId", eventId)
                .setParameter("fixtureId", fixtureId)
                .getResultList();

        List<UUID> all = teamRows.stream().map(r -> uuid(r[0])).toList();
        List<UUID> playing = teamRows.stream()
                .filter(r -> !"bye".equals(r[1]))
                .map(r -> uuid(r[0]))
                .toList();

        return new FixtureInfo(
                fixtureId, eventGameId, status, roundNumber,
                (String) game[0], (String) game[1], all, playing
        );
    }

    private FixtureCreditsResponse view(UUID eventId, FixtureInfo fixture) {
        List<String> segments = PlayerPointsRules.segmentsFor(fixture.scoringHandler());
        String unavailable = segments.isEmpty()
                ? "This activity has no star players."
                : fixture.playingTeamIds().isEmpty() ? "No team plays in this match." : null;

        List<Credit> credits = loadLiveAwards(eventId, fixture.fixtureId()).stream()
                .map(a -> new Credit(a.segmentIndex(), a.participantId()))
                .toList();

        return new FixtureCreditsResponse(
                fixture.fixtureId(),
                fixture.gameName(),
                fixture.roundNumber(),
                segments,
                loadPlayers(eventId, fixture.playingTeamIds()),
                credits,
                unavailable
        );
    }

    /** Players currently in the playing teams, captain first. */
    private List<FixtureTeamPlayers> loadPlayers(UUID eventId, List<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> teamRows = entityManager.createNativeQuery("""
                        SELECT t.id, t.name, t.color
                        FROM murise.teams t
                        WHERE t.event_id = :eventId AND t.id IN (:teamIds)
                        """)
                .setParameter("eventId", eventId)
                .setParameter("teamIds", teamIds)
                .getResultList();

        @SuppressWarnings("unchecked")
        List<Object[]> playerRows = entityManager.createNativeQuery("""
                        SELECT ep.id, ep.user_id, ep.display_name, ep.team_id
                        FROM murise.event_participants ep
                        WHERE ep.event_id = :eventId
                          AND ep.team_id IN (:teamIds)
                          AND ep.status IN ('active', 'benched')
                        ORDER BY ep.is_captain DESC, lower(ep.display_name)
                        """)
                .setParameter("eventId", eventId)
                .setParameter("teamIds", teamIds)
                .getResultList();

        Map<UUID, List<FixturePlayer>> byTeam = playerRows.stream().collect(Collectors.groupingBy(
                r -> uuid(r[3]),
                LinkedHashMap::new,
                Collectors.mapping(
                        r -> new FixturePlayer(uuid(r[0]), uuid(r[1]), (String) r[2]),
                        Collectors.toList()
                )
        ));

        Map<UUID, Object[]> teamById = teamRows.stream()
                .collect(Collectors.toMap(r -> uuid(r[0]), r -> r));

        List<FixtureTeamPlayers> result = new ArrayList<>();
        for (UUID teamId : teamIds) {
            Object[] team = teamById.get(teamId);
            if (team == null) {
                continue;
            }
            result.add(new FixtureTeamPlayers(
                    teamId,
                    (String) team[1],
                    (String) team[2],
                    List.copyOf(byTeam.getOrDefault(teamId, List.of()))
            ));
        }
        return List.copyOf(result);
    }

    private List<LiveAward> loadLiveAwards(UUID eventId, UUID fixtureId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT a.id, a.segment_index, a.participant_id
                        FROM murise.player_points a
                        WHERE a.event_id = :eventId
                          AND a.fixture_id = :fixtureId
                          AND a.kind = 'award'
                          AND NOT EXISTS (
                              SELECT 1 FROM murise.player_points r
                              WHERE r.event_id = a.event_id AND r.reverses_id = a.id
                          )
                        ORDER BY a.segment_index, a.created_at
                        """)
                .setParameter("eventId", eventId)
                .setParameter("fixtureId", fixtureId)
                .getResultList();

        return rows.stream()
                .map(r -> new LiveAward(uuid(r[0]), ((Number) r[1]).intValue(), uuid(r[2])))
                .toList();
    }

    private void insertAward(
            UUID eventId,
            UUID fixtureId,
            Credit credit,
            String segmentLabel,
            UUID teamId,
            UUID actorId
    ) {
        entityManager.createNativeQuery("""
                        INSERT INTO murise.player_points (
                            event_id, fixture_id, segment_index, segment_label,
                            participant_id, team_id, kind, points, actor_id
                        )
                        VALUES (
                            :eventId, :fixtureId, :segmentIndex, :segmentLabel,
                            :participantId, :teamId, 'award', 1, :actorId
                        )
                        """)
                .setParameter("eventId", eventId)
                .setParameter("fixtureId", fixtureId)
                .setParameter("segmentIndex", credit.segmentIndex())
                .setParameter("segmentLabel", segmentLabel)
                .setParameter("participantId", credit.participantId())
                .setParameter("teamId", teamId)
                .setParameter("actorId", actorId)
                .executeUpdate();
    }

    /** Copies the award's fixture, segment, player and team so the row stands on its own. */
    private void insertReversal(UUID eventId, UUID actorId, UUID awardId) {
        entityManager.createNativeQuery("""
                        INSERT INTO murise.player_points (
                            event_id, fixture_id, segment_index, segment_label,
                            participant_id, team_id, kind, points, reverses_id, actor_id
                        )
                        SELECT a.event_id, a.fixture_id, a.segment_index, a.segment_label,
                               a.participant_id, a.team_id, 'reversal', -1, a.id, :actorId
                        FROM murise.player_points a
                        WHERE a.event_id = :eventId AND a.id = :awardId AND a.kind = 'award'
                        """)
                .setParameter("eventId", eventId)
                .setParameter("awardId", awardId)
                .setParameter("actorId", actorId)
                .executeUpdate();
    }

    private void recordAudit(UUID eventId, UUID actorId, UUID fixtureId, PlayerPointsRules.Diff diff) {
        entityManager.createNativeQuery("""
                        INSERT INTO murise.audit_events (
                            event_id, actor_id, action, entity_type, entity_id, details
                        )
                        VALUES (
                            :eventId, :actorId, 'player_points_updated', 'fixture', :fixtureId,
                            jsonb_build_object(
                                'awarded', CAST(:awarded AS integer),
                                'reversed', CAST(:reversed AS integer)
                            )
                        )
                        """)
                .setParameter("eventId", eventId)
                .setParameter("actorId", actorId)
                .setParameter("fixtureId", fixtureId)
                .setParameter("awarded", diff.toAward().size())
                .setParameter("reversed", diff.toReverse().size())
                .executeUpdate();
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime o) return o.toInstant();
        if (value instanceof java.sql.Timestamp t) return t.toInstant();
        return Instant.parse(value.toString());
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
