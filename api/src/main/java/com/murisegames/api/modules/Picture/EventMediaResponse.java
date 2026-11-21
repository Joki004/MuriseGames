package com.murisegames.api.modules.Picture;

import java.time.Instant;
import java.util.UUID;

public record EventMediaResponse(
        UUID id,
        UUID eventId,
        UUID ownerId,
        String mimeType,
        long byteSize,
        String visibility,
        String signedUrl,
        Instant createdAt
) {
}