package com.murisegames.api.modules.Event;

import com.murisegames.api.modules.Event.Membership.EventMembership;
import com.murisegames.api.modules.Event.Membership.EventMembershipRepository;
import com.murisegames.api.modules.Event.Membership.EventRoleGrant;
import com.murisegames.api.modules.Event.Membership.EventRoleGrantRepository;
import com.murisegames.api.modules.Event.dto.EventCreateRequest;
import com.murisegames.api.modules.User.User;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMembershipRepository membershipRepository;
    private final EventRoleGrantRepository roleGrantRepository;
    private final UserService userService;
    private final EntityManager entityManager;

    @Transactional
    public EventResponse create(Jwt jwt, EventCreateRequest request) {
        String timezone = request.timezone().trim();

        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use a valid timezone such as Europe/Warsaw."
            );
        }

        User creator = userService.resolveOrProvision(jwt);

        Event event = new Event();
        event.setName(request.name().trim());
        event.setTheme(optionalText(request.theme()));
        event.setStartsAt(request.startsAt().toInstant());
        event.setTimezone(timezone);
        event.setVenue(optionalText(request.venue()));
        event.setStatus("draft");
        event.setVisibility(request.visibility());
        event.setScoreboardVisibility("hidden");
        event.setCreatedBy(creator.getId());

        // Flush parents before inserting rows referencing their IDs.
        eventRepository.saveAndFlush(event);

        EventMembership membership = new EventMembership();
        membership.setEventId(event.getId());
        membership.setUserId(creator.getId());
        membership.setStatus("active");
        membershipRepository.saveAndFlush(membership);

        EventRoleGrant organizerGrant = new EventRoleGrant();
        organizerGrant.setEventId(event.getId());
        organizerGrant.setMembershipId(membership.getId());
        organizerGrant.setRole("ORGANIZER");
        organizerGrant.setGrantedBy(creator.getId());
        roleGrantRepository.saveAndFlush(organizerGrant);

        // Load timestamps supplied by PostgreSQL.
        entityManager.refresh(event);

        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public Page<EventResponse> listMine(Jwt jwt, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100."
            );
        }

        UUID userId = userService.extractUserId(jwt);

        PageRequest pagination = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("startsAt"),
                        Sort.Order.desc("id")
                )
        );

        return eventRepository
                .findAccessibleEvents(userId, pagination)
                .map(EventResponse::from);
    }

    private String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}