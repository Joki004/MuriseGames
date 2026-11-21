package com.murisegames.api.modules.Picture;

public enum PictureSlot {
    AVATAR(512, 512),
    EVENT_ICON(512, 512),
    EVENT_COVER(1600, 900),
    TEAM_ICON(512, 512);

    private final int width;
    private final int height;

    PictureSlot(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}