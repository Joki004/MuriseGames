package com.murisegames.api.modules.Picture;

public record PictureResponse(
        PictureSlot slot,
        String emoji,
        String imageUrl,
        boolean canEdit
) {
}