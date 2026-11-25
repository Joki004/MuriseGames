package com.murisegames.api.modules.Event.Roster;

import com.murisegames.api.modules.Event.Participant.EventParticipant;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.PictureRow;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.RosterMember;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.RosterPicture;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.RosterTeam;
import com.murisegames.api.modules.Event.Team.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure assembly of the roster, kept free of Spring so it is easy to test. */
public final class EventRosterAssembler {

    /** Only people who take part are shown; invited and removed people are not. */
    static final Set<String> SHOWN_STATUSES = Set.of("active", "benched");

    private EventRosterAssembler() {
    }

    public record Result(List<RosterTeam> teams, int unassignedCount) {
    }

    public static Result assemble(
            List<Team> teams,
            List<EventParticipant> participants,
            List<PictureRow> pictures,
            Map<String, String> signedUrls
    ) {
        Map<UUID, RosterPicture> teamPictures = new HashMap<>();
        Map<UUID, RosterPicture> avatars = new HashMap<>();

        for (PictureRow row : pictures) {
            RosterPicture picture = toPicture(row, signedUrls);

            if ("TEAM_ICON".equals(row.slot()) && row.teamId() != null) {
                teamPictures.put(row.teamId(), picture);
            } else if ("AVATAR".equals(row.slot()) && row.userId() != null) {
                avatars.put(row.userId(), picture);
            }
        }

        Map<UUID, List<RosterMember>> membersByTeam = new HashMap<>();
        int unassigned = 0;

        for (EventParticipant participant : participants) {
            if (!SHOWN_STATUSES.contains(participant.getStatus())) {
                continue;
            }

            if (participant.getTeamId() == null) {
                unassigned++;
                continue;
            }

            RosterPicture avatar = participant.getUserId() == null
                    ? RosterPicture.NONE
                    : avatars.getOrDefault(participant.getUserId(), RosterPicture.NONE);

            membersByTeam
                    .computeIfAbsent(participant.getTeamId(), id -> new ArrayList<>())
                    .add(new RosterMember(
                            participant.getId(),
                            participant.getUserId(),
                            participant.getDisplayName(),
                            participant.isCaptain(),
                            avatar
                    ));
        }

        Comparator<RosterMember> memberOrder = Comparator
                .comparing((RosterMember m) -> !m.captain())
                .thenComparing(m -> m.displayName().toLowerCase());

        List<RosterTeam> result = new ArrayList<>();

        for (Team team : teams) {
            List<RosterMember> members = new ArrayList<>(
                    membersByTeam.getOrDefault(team.getId(), List.of())
            );
            members.sort(memberOrder);

            result.add(new RosterTeam(
                    team.getId(),
                    team.getName(),
                    team.getColor(),
                    team.getSortOrder(),
                    teamPictures.getOrDefault(team.getId(), RosterPicture.NONE),
                    List.copyOf(members)
            ));
        }

        return new Result(List.copyOf(result), unassigned);
    }

    private static RosterPicture toPicture(PictureRow row, Map<String, String> signedUrls) {
        String url = row.objectPath() == null ? null : signedUrls.get(row.objectPath());
        String emoji = row.emoji() == null || row.emoji().isBlank() ? null : row.emoji();
        return new RosterPicture(emoji, url);
    }
}
