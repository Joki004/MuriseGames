package com.murisegames.api.modules.Event.Roster;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.EventRosterResponse;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.PictureRow;
import com.murisegames.api.modules.Picture.SupabasePictureStorage;
import com.murisegames.api.modules.User.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventRosterService {

    /** Long enough for a TV left on a page; clients refresh well before. */
    static final int IMAGE_URL_SECONDS = 900;

    private final UserService users;
    private final EventMembershipService memberships;
    private final EventRosterQueries queries;
    private final SupabasePictureStorage storage;

    /** Any member of the event (player, referee, organizer) can see the roster. */
    public EventRosterResponse roster(Jwt jwt, UUID eventId) {
        UUID actorId = users.extractUserId(jwt);
        memberships.requireAccess(eventId, actorId);

        EventRosterQueries.Loaded loaded = queries.load(eventId);
        Instant now = Instant.now();

        EventRosterAssembler.Result assembled = EventRosterAssembler.assemble(
                loaded.teams(),
                loaded.participants(),
                loaded.pictures(),
                sign(loaded.pictures())
        );

        return new EventRosterResponse(
                eventId,
                now,
                now.plusSeconds(IMAGE_URL_SECONDS),
                assembled.teams(),
                assembled.unassignedCount()
        );
    }

    /** A storage outage must not break the screens: they fall back to emoji or initials. */
    private Map<String, String> sign(List<PictureRow> pictures) {
        List<String> paths = pictures.stream()
                .map(PictureRow::objectPath)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (paths.isEmpty()) {
            return Map.of();
        }

        try {
            return storage.signedUrls(paths, IMAGE_URL_SECONDS);
        } catch (ResponseStatusException | IllegalStateException exception) {
            log.warn("Roster pictures could not be signed; showing fallbacks. count={}",
                    paths.size());
            return Map.of();
        }
    }
}
