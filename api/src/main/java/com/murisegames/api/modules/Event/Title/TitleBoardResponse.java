package com.murisegames.api.modules.Event.Title;

import java.util.List;
import java.util.UUID;

public record TitleBoardResponse(List<TeamTitle> teams) {

    /** title and answerScope are null until the host registers them. */
    public record TeamTitle(
            UUID teamId,
            String teamName,
            String teamColor,
            String title,
            String answerScope,
            List<String> aliases
    ) {
    }
}