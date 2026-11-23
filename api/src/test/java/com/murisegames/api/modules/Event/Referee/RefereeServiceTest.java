package com.murisegames.api.modules.Event.Referee;

import com.murisegames.api.modules.Event.Referee.RefereeService.FixtureInfo;
import com.murisegames.api.modules.Event.Referee.RefereeTypes.DeskFixtureTeam;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefereeServiceTest {

    private final UUID aka = UUID.randomUUID();
    private final UUID murise = UUID.randomUUID();
    private final UUID fantome = UUID.randomUUID();
    private final UUID poison = UUID.randomUUID();
    private final UUID alibi = UUID.randomUUID();

    // ---- Own team rule (blocking) ----

    @Test
    void refereeCannotRecordTheirOwnTeamsMatch() {
        assertThat(RefereeService.isOwnTeamMatch(aka, List.of(aka, murise))).isTrue();
        assertThat(RefereeService.isOwnTeamMatch(murise, List.of(aka, murise))).isTrue();
    }

    @Test
    void refereeCanRecordOtherTeamsMatches() {
        assertThat(RefereeService.isOwnTeamMatch(fantome, List.of(aka, murise))).isFalse();
    }

    @Test
    void neutralRefereeWithoutATeamCanRecordAnyMatch() {
        assertThat(RefereeService.isOwnTeamMatch(null, List.of(aka, murise))).isFalse();
    }

    @Test
    void soloMatchOfTheRefereesOwnTeamIsAlsoBlocked() {
        assertThat(RefereeService.isOwnTeamMatch(aka, List.of(aka))).isTrue();
    }

    // ---- Team playing at the same time (warning only) ----

    private FixtureInfo fixture(UUID game, String start, String end, String status, UUID... teams) {
        List<DeskFixtureTeam> list = new ArrayList<>();
        for (UUID t : teams) list.add(new DeskFixtureTeam(t, "Team", "home"));
        return new FixtureInfo(UUID.randomUUID(), game, "Game", 1, status,
                Instant.parse("2026-11-28T" + start + ":00Z"),
                Instant.parse("2026-11-28T" + end + ":00Z"), null, list);
    }

    @Test
    void refereeIsBusyWhenTheirTeamPlaysAnOverlappingMatch() {
        // 19:00 Poison: Fantome Rouge vs Aka; 19:00 Unified Alibi: Murise.
        var poisonR1 = fixture(poison, "19:00", "19:30", "scheduled", fantome, aka);
        var alibiR1 = fixture(alibi, "19:00", "19:30", "scheduled", murise);
        var all = List.of(poisonR1, alibiR1);

        // A Fantome Rouge referee watching Murise's Unified Alibi: busy (warning).
        assertThat(RefereeService.teamBusy(fantome, alibiR1, all)).isTrue();
        // A Murise referee watching the Poison match: busy too.
        assertThat(RefereeService.teamBusy(murise, poisonR1, all)).isTrue();
    }

    @Test
    void refereeIsFreeWhenTheirTeamPlaysAtAnotherTime() {
        var poisonR1 = fixture(poison, "19:00", "19:30", "scheduled", fantome, aka);
        var alibiR3 = fixture(alibi, "20:10", "20:40", "scheduled", fantome);
        var all = List.of(poisonR1, alibiR3);

        assertThat(RefereeService.teamBusy(aka, alibiR3, all)).isFalse();
    }

    @Test
    void backToBackMatchesDoNotOverlap() {
        var early = fixture(poison, "19:00", "19:30", "scheduled", aka, murise);
        var later = fixture(alibi, "19:30", "20:00", "scheduled", fantome);

        assertThat(RefereeService.teamBusy(aka, later, List.of(early, later))).isFalse();
    }

    @Test
    void cancelledMatchesAndNeutralRefereesAreNeverBusy() {
        var cancelled = fixture(poison, "19:00", "19:30", "voided", aka, murise);
        var match = fixture(alibi, "19:00", "19:30", "scheduled", fantome);
        var all = List.of(cancelled, match);

        assertThat(RefereeService.teamBusy(aka, match, all)).isFalse();
        assertThat(RefereeService.teamBusy(null, match, all)).isFalse();
    }
}
