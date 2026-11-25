package com.murisegames.api.modules.Event.Roster;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Who is in which team, with the pictures the screens need, in one response.
 * Image URLs are short-lived signed URLs: clients refetch before expiresAt.
 */
public final class EventRosterTypes {

    private EventRosterTypes() {
    }

    /** emoji and imageUrl may both be null: clients then show initials. */
    public record RosterPicture(String emoji, String imageUrl) {

        public static final RosterPicture NONE = new RosterPicture(null, null);
    }

    public record RosterMember(
            UUID participantId,
            UUID userId,
            String displayName,
            boolean captain,
            RosterPicture picture
    ) {
    }

    public record RosterTeam(
            UUID teamId,
            String name,
            String color,
            int sortOrder,
            RosterPicture picture,
            List<RosterMember> members
    ) {
    }

    public record EventRosterResponse(
            UUID eventId,
            Instant serverTime,
            Instant imagesExpireAt,
            List<RosterTeam> teams,
            int unassignedCount
    ) {
    }

    /** One row of murise.pictures joined with its ready asset, if any. */
    public record PictureRow(
            String slot,
            UUID teamId,
            UUID userId,
            String emoji,
            String objectPath
    ) {
    }
}
