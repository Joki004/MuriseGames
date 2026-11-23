package com.murisegames.api.modules.Event.Scoring;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Scoring.dto.ScoreEntryCreateRequest;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoringService {

    private final ScoreEntryRepository scoreEntryRepository;
    private final ScoreboardSnapshotRepository snapshotRepository;
    private final ScoreboardSnapshotRowRepository snapshotRowRepository;
    private final TeamRepository teamRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<ScoreEntryResponse> listEntries(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        List<ScoreEntry> entries = scoreEntryRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        Set<UUID> reversedIds = entries.stream()
                .map(ScoreEntry::getReversesEntryId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<UUID, Team> teams = teamRepository
                .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId)
                .stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));

        return entries.stream()
                .map(entry -> toEntryResponse(
                        entry,
                        teams.get(entry.getTeamId()),
                        reversedIds.contains(entry.getId())
                ))
                .toList();
    }

    @Transactional
    public ScoreEntryResponse addEntry(
            Jwt jwt,
            UUID eventId,
            ScoreEntryCreateRequest request
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        Team team = teamRepository.findByIdAndEventId(request.teamId(), eventId)
                .orElseThrow(() -> new EntityNotFoundException("Team not found."));

        String sourceKey = (request.sourceKey() != null && !request.sourceKey().isBlank())
                ? request.sourceKey().trim()
                : "manual:" + UUID.randomUUID();

        var existing = scoreEntryRepository.findByEventIdAndSourceKey(eventId, sourceKey);
        if (existing.isPresent()) {
            ScoreEntry recorded = existing.get();
            boolean isReversed = scoreEntryRepository.existsByReversesEntryId(recorded.getId());
            return toEntryResponse(recorded, team, isReversed);
        }

        ScoreEntry entry = new ScoreEntry();
        entry.setEventId(eventId);
        entry.setTeamId(team.getId());
        entry.setCategory(request.category().trim());
        entry.setDelta(request.delta());
        entry.setSourceKey(sourceKey);
        entry.setReason(request.reason().trim());
        entry.setActorId(actorId);

        scoreEntryRepository.saveAndFlush(entry);
        entityManager.refresh(entry);

        return toEntryResponse(entry, team, false);
    }

    @Transactional
    public ScoreEntryResponse reverseEntry(
            Jwt jwt,
            UUID eventId,
            UUID entryId,
            String reason
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        ScoreEntry original = scoreEntryRepository.findByIdAndEventId(entryId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Score entry not found."));

        if ("reversal".equals(original.getCategory())) {
            throw new IllegalStateException("A reversal entry cannot be reversed.");
        }
        if (scoreEntryRepository.existsByReversesEntryId(original.getId())) {
            throw new IllegalStateException("This score entry has already been reversed.");
        }

        ScoreEntry reversal = new ScoreEntry();
        reversal.setEventId(eventId);
        reversal.setTeamId(original.getTeamId());
        reversal.setResultId(original.getResultId());
        reversal.setCategory("reversal");
        reversal.setDelta(original.getDelta().negate());
        reversal.setSourceKey("reversal:" + original.getId());
        reversal.setReason(reason.trim());
        reversal.setActorId(actorId);
        reversal.setReversesEntryId(original.getId());

        scoreEntryRepository.saveAndFlush(reversal);
        entityManager.refresh(reversal);

        Team team = teamRepository.findByIdAndEventId(original.getTeamId(), eventId).orElse(null);
        return toEntryResponse(reversal, team, false);
    }

    @Transactional(readOnly = true)
    public ScoreboardResponse getScoreboard(Jwt jwt, UUID eventId) {
        UUID actorId = userService.extractUserId(jwt);
        Set<String> roles = membershipService.requireAccess(eventId, actorId);
        boolean isOrganizer = roles.contains("ORGANIZER");

        EventScoreboardState state = loadEventScoreboardState(eventId);

        if ("hidden".equals(state.visibility()) && !isOrganizer) {
            return new ScoreboardResponse(
                    eventId,
                    state.visibility(),
                    true,
                    null,
                    null,
                    List.of()
            );
        }

        if ("frozen".equals(state.visibility()) && state.frozenSnapshotId() != null) {
            ScoreboardSnapshot snapshot = snapshotRepository
                    .findByIdAndEventId(state.frozenSnapshotId(), eventId)
                    .orElseThrow(() -> new EntityNotFoundException("Frozen snapshot not found."));

            Map<UUID, Team> teams = teamRepository
                    .findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId)
                    .stream()
                    .collect(Collectors.toMap(Team::getId, Function.identity()));

            List<ScoreboardRowResponse> rows = snapshotRowRepository
                    .findBySnapshotIdOrderByRankAsc(snapshot.getId())
                    .stream()
                    .map(row -> {
                        Team team = teams.get(row.getTeamId());
                        return new ScoreboardRowResponse(
                                row.getTeamId(),
                                team != null ? team.getName() : "Unknown Team",
                                team != null ? team.getColor() : "#64748b",
                                row.getPoints(),
                                row.getRank()
                        );
                    })
                    .toList();

            return new ScoreboardResponse(
                    eventId,
                    state.visibility(),
                    false,
                    snapshot.getId(),
                    snapshot.getCreatedAt(),
                    rows
            );
        }

        return new ScoreboardResponse(
                eventId,
                state.visibility(),
                false,
                null,
                null,
                computeLiveStandings(eventId)
        );
    }

    @Transactional
    public ScoreboardResponse updateVisibility(
            Jwt jwt,
            UUID eventId,
            String targetVisibility
    ) {
        UUID actorId = userService.extractUserId(jwt);
        membershipService.requireOrganizer(eventId, actorId);

        UUID snapshotId = null;

        if ("frozen".equals(targetVisibility)) {
            List<ScoreboardRowResponse> liveRows = computeLiveStandings(eventId);

            ScoreboardSnapshot snapshot = new ScoreboardSnapshot();
            snapshot.setEventId(eventId);
            snapshot.setCreatedBy(actorId);
            snapshotRepository.saveAndFlush(snapshot);

            for (ScoreboardRowResponse row : liveRows) {
                ScoreboardSnapshotRow entity = new ScoreboardSnapshotRow();
                entity.setSnapshotId(snapshot.getId());
                entity.setTeamId(row.teamId());
                entity.setEventId(eventId);
                entity.setPoints(row.points());
                entity.setRank(row.rank());
                snapshotRowRepository.save(entity);
            }
            snapshotRowRepository.flush();
            snapshotId = snapshot.getId();
        }

        entityManager.createNativeQuery("""
                UPDATE murise.events
                SET scoreboard_visibility = :visibility,
                    frozen_snapshot_id = :snapshotId
                WHERE id = :eventId
                """)
                .setParameter("visibility", targetVisibility)
                .setParameter("snapshotId", snapshotId)
                .setParameter("eventId", eventId)
                .executeUpdate();

        return getScoreboard(jwt, eventId);
    }

    /**
     * Live standings WITHOUT a permission check. Only call after the caller has
     * decided this viewer may see the full scoreboard (e.g. team results mode).
     */
    @Transactional(readOnly = true)
    public List<ScoreboardRowResponse> liveStandingsUnchecked(UUID eventId) {
        return computeLiveStandings(eventId);
    }

    private List<ScoreboardRowResponse> computeLiveStandings(UUID eventId) {
        List<Team> activeTeams =
                teamRepository.findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId);

        Map<UUID, BigDecimal> totalsByTeam = scoreEntryRepository.sumPointsByTeam(eventId)
                .stream()
                .collect(Collectors.toMap(
                        ScoreEntryRepository.TeamPointTotal::getTeamId,
                        ScoreEntryRepository.TeamPointTotal::getTotalPoints
                ));

        List<Team> sorted = new ArrayList<>(activeTeams);
        sorted.sort(Comparator
                .comparing((Team t) -> totalsByTeam.getOrDefault(t.getId(), BigDecimal.ZERO))
                .reversed()
                .thenComparingInt(Team::getSortOrder)
                .thenComparing(Team::getName, String.CASE_INSENSITIVE_ORDER));

        List<ScoreboardRowResponse> rows = new ArrayList<>();
        BigDecimal previousPoints = null;
        int currentRank = 0;

        for (int i = 0; i < sorted.size(); i++) {
            Team team = sorted.get(i);
            BigDecimal points = totalsByTeam.getOrDefault(team.getId(), BigDecimal.ZERO);

            if (previousPoints == null || points.compareTo(previousPoints) != 0) {
                currentRank = i + 1;
                previousPoints = points;
            }

            rows.add(new ScoreboardRowResponse(
                    team.getId(),
                    team.getName(),
                    team.getColor(),
                    points,
                    currentRank
            ));
        }

        return rows;
    }

    private EventScoreboardState loadEventScoreboardState(UUID eventId) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                SELECT scoreboard_visibility, frozen_snapshot_id
                FROM murise.events
                WHERE id = :eventId
                """)
                .setParameter("eventId", eventId)
                .getSingleResult();

        return new EventScoreboardState(
                (String) row[0],
                row[1] != null ? UUID.fromString(row[1].toString()) : null
        );
    }

    private ScoreEntryResponse toEntryResponse(
            ScoreEntry entry,
            Team team,
            boolean reversed
    ) {
        return new ScoreEntryResponse(
                entry.getId(),
                entry.getEventId(),
                entry.getTeamId(),
                team != null ? team.getName() : "Unknown Team",
                entry.getResultId(),
                entry.getCategory(),
                entry.getDelta(),
                entry.getSourceKey(),
                entry.getReason(),
                entry.getActorId(),
                entry.getReversesEntryId(),
                reversed,
                entry.getCreatedAt()
        );
    }

    private record EventScoreboardState(String visibility, UUID frozenSnapshotId) {
    }
}