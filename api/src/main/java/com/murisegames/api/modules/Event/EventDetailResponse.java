package com.murisegames.api.modules.Event;

import java.util.Set;

public record EventDetailResponse(
        EventResponse event,
        Set<String> roles
) {
}