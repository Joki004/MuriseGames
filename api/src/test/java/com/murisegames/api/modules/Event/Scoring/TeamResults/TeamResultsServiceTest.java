package com.murisegames.api.modules.Event.Scoring.TeamResults;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamResultsServiceTest {

    @Test
    void parsesStoredRoundValues() {
        assertThat(TeamResultsService.parseValues("{\"values\": [1, 0, 1, 1]}"))
                .isEqualTo(List.of(1, 0, 1, 1));
        assertThat(TeamResultsService.parseValues("{\"values\":[3,2,0,1]}"))
                .isEqualTo(List.of(3, 2, 0, 1));
    }

    @Test
    void missingOrEmptyEvidenceGivesNoValues() {
        assertThat(TeamResultsService.parseValues(null)).isEmpty();
        assertThat(TeamResultsService.parseValues("{}")).isEmpty();
        assertThat(TeamResultsService.parseValues("{\"values\": []}")).isEmpty();
    }
}
