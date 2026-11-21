package com.murisegames.api.modules.Event.Scoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScoreboardResponse(
        UUID eventId,
        String visibility,
        boolean hiddenFromViewer,
        UUID frozenSnapshotId,
        Instant frozenAt,
        List<ScoreboardRowResponse> rows
) {
}