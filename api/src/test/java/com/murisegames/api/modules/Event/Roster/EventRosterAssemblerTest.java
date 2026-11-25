package com.murisegames.api.modules.Event.Roster;

import com.murisegames.api.modules.Event.Participant.EventParticipant;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.PictureRow;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.RosterTeam;
import com.murisegames.api.modules.Event.Team.Team;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventRosterAssemblerTest {

    private static final UUID EVENT = UUID.randomUUID();

    @Test
    void groupsMembersByTeamWithCaptainFirstAndSkipsInvitedOrRemoved() {
        Team red = team("Red", 1);
        Team blue = team("Blue", 2);

        EventParticipant zoe = participant("Zoe", red.getId(), "active", false);
        EventParticipant adam = participant("adam", red.getId(), "active", false);
        EventParticipant cap = participant("Mia", red.getId(), "active", true);
        EventParticipant benched = participant("Bea", blue.getId(), "benched", false);
        EventParticipant invited = participant("Ivo", blue.getId(), "invited", false);
        EventParticipant removed = participant("Rex", blue.getId(), "removed", false);
        EventParticipant loose = participant("Lou", null, "active", false);

        EventRosterAssembler.Result result = EventRosterAssembler.assemble(
                List.of(red, blue),
                List.of(zoe, adam, cap, benched, invited, removed, loose),
                List.of(),
                Map.of()
        );

        RosterTeam first = result.teams().get(0);
        assertEquals("Red", first.name());
        assertEquals(List.of("Mia", "adam", "Zoe"),
                first.members().stream().map(m -> m.displayName()).toList());
        assertTrue(first.members().get(0).captain());

        RosterTeam second = result.teams().get(1);
        assertEquals(List.of("Bea"),
                second.members().stream().map(m -> m.displayName()).toList());

        assertEquals(1, result.unassignedCount());
    }

    @Test
    void usesSignedUrlWhenAvailableAndKeepsEmojiOtherwise() {
        Team red = team("Red", 1);
        Team blue = team("Blue", 2);
        EventParticipant mia = participant("Mia", red.getId(), "active", true);
        EventParticipant bea = participant("Bea", blue.getId(), "active", false);

        List<PictureRow> pictures = List.of(
                new PictureRow("TEAM_ICON", red.getId(), null, null, "a.jpg"),
                new PictureRow("TEAM_ICON", blue.getId(), null, "🦊", "b.jpg"),
                new PictureRow("AVATAR", null, mia.getUserId(), "🔥", null),
                new PictureRow("AVATAR", null, UUID.randomUUID(), "🐼", null)
        );

        EventRosterAssembler.Result result = EventRosterAssembler.assemble(
                List.of(red, blue),
                List.of(mia, bea),
                pictures,
                Map.of("a.jpg", "https://signed/a")
        );

        RosterTeam redTeam = result.teams().get(0);
        assertEquals("https://signed/a", redTeam.picture().imageUrl());

        RosterTeam blueTeam = result.teams().get(1);
        // Signing failed for b.jpg: the emoji is still there for the fallback.
        assertNull(blueTeam.picture().imageUrl());
        assertEquals("🦊", blueTeam.picture().emoji());

        assertEquals("🔥", redTeam.members().get(0).picture().emoji());
        assertNull(blueTeam.members().get(0).picture().emoji());
    }

    private static Team team(String name, int order) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setEventId(EVENT);
        team.setName(name);
        team.setColor("#10b981");
        team.setSortOrder(order);
        team.setActive(true);
        return team;
    }

    private static EventParticipant participant(
            String name, UUID teamId, String status, boolean captain
    ) {
        EventParticipant participant = new EventParticipant();
        participant.setId(UUID.randomUUID());
        participant.setEventId(EVENT);
        participant.setUserId(UUID.randomUUID());
        participant.setDisplayName(name);
        participant.setTeamId(teamId);
        participant.setStatus(status);
        participant.setCaptain(captain);
        return participant;
    }
}
