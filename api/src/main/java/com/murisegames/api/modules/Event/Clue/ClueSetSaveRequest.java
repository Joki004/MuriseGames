package com.murisegames.api.modules.Event.Clue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ClueSetSaveRequest(
        @NotNull
        @Size(min = 3, max = 3, message = "Exactly three clues are required.")
        List<@Valid Item> clues
) {

    public record Item(
            @NotNull
            @Size(max = 280, message = "A clue can have at most 280 characters.")
            String body,

            boolean approved
    ) {
    }
}