package com.murisegames.api.modules.Event.Clue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClueReleaseServiceTest {

    @Test
    void staleExpectedStageReturns409Conflict() {
        var current = new ClueReleaseService.StateRow(1, "open", 1L);

        assertThatThrownBy(() ->
                ClueReleaseService.validateExpectedState(current, 0, 0L, true)
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void staleExpectedVersionReturns409Conflict() {
        var current = new ClueReleaseService.StateRow(1, "open", 2L);

        assertThatThrownBy(() ->
                ClueReleaseService.validateExpectedState(current, 1, 1L, true)
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void revealIsRefusedWhileGuessingIsOpen() {
        var open = new ClueReleaseService.StateRow(3, "open", 3L);

        assertThatThrownBy(() -> ClueReleaseService.requireClosedForReveal(open))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void revealIsAllowedOnceGuessingIsClosed() {
        var closed = new ClueReleaseService.StateRow(3, "closed", 4L);

        ClueReleaseService.requireClosedForReveal(closed); // no exception
    }

    @Test
    void closedGuessingRejectsFurtherStageChangesWith409() {
        var current = new ClueReleaseService.StateRow(3, "closed", 4L);

        assertThatThrownBy(() ->
                ClueReleaseService.validateExpectedState(current, 3, 4L, true)
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}