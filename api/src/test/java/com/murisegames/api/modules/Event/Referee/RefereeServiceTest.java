package com.murisegames.api.modules.Event.Referee;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefereeServiceTest {

    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();
    private final UUID teamC = UUID.randomUUID();

    @Test
    void refereeCannotRecordTheirOwnTeamsMatch() {
        assertThat(RefereeService.isOwnTeamMatch(teamA, List.of(teamA, teamB))).isTrue();
        assertThat(RefereeService.isOwnTeamMatch(teamB, List.of(teamA, teamB))).isTrue();
    }

    @Test
    void refereeCanRecordOtherTeamsMatches() {
        assertThat(RefereeService.isOwnTeamMatch(teamC, List.of(teamA, teamB))).isFalse();
    }

    @Test
    void neutralRefereeWithoutATeamCanRecordAnyMatch() {
        assertThat(RefereeService.isOwnTeamMatch(null, List.of(teamA, teamB))).isFalse();
    }

    @Test
    void soloMatchOfTheRefereesOwnTeamIsAlsoBlocked() {
        assertThat(RefereeService.isOwnTeamMatch(teamA, List.of(teamA))).isTrue();
    }
}
