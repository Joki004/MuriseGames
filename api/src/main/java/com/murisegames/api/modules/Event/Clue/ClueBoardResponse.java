package com.murisegames.api.modules.Event.Clue;

import java.util.List;
import java.util.UUID;

public record ClueBoardResponse(List<TeamClues> teams, List<Stage> stages) {

    public record Clue(int stage, String body, boolean approved) {
    }

    public record TeamClues(
            UUID teamId,
            String teamName,
            String teamColor,
            List<Clue> clues
    ) {
    }

    public record Stage(
            int stage,
            int approvedTeams,
            int totalTeams,
            boolean canRelease
    ) {
    }
}