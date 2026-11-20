package com.murisegames.api.modules.Event.Team;

import java.util.List;

public record TeamListResponse(
        List<TeamResponse> teams,
        boolean canManage
) {
}