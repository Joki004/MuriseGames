package com.murisegames.api.modules.Event.Roster;

import com.murisegames.api.modules.Event.Participant.EventParticipant;
import com.murisegames.api.modules.Event.Participant.EventParticipantRepository;
import com.murisegames.api.modules.Event.Roster.EventRosterTypes.PictureRow;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.Picture.SupabasePictureStorage;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Database reads for the roster. Signing image URLs happens outside this transaction. */
@Service
@RequiredArgsConstructor
public class EventRosterQueries {

    private final TeamRepository teams;
    private final EventParticipantRepository participants;
    private final EntityManager entityManager;

    public record Loaded(
            List<Team> teams,
            List<EventParticipant> participants,
            List<PictureRow> pictures
    ) {
    }

    @Transactional(readOnly = true)
    public Loaded load(UUID eventId) {
        return new Loaded(
                teams.findByEventIdAndActiveTrueOrderBySortOrderAscNameAsc(eventId),
                participants.findByEventIdOrderByCreatedAtAsc(eventId),
                pictures(eventId)
        );
    }

    /**
     * Team icons of this event's teams and avatars of this event's participants.
     * An image is only returned when its asset is ready in the picture bucket;
     * otherwise the row still carries its emoji.
     */
    @SuppressWarnings("unchecked")
    private List<PictureRow> pictures(UUID eventId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select p.slot,
                               p.team_id,
                               p.user_id,
                               p.emoji,
                               case
                                   when a.status = 'ready'
                                    and a.purpose = 'picture'
                                    and a.bucket = :bucket
                                   then a.object_path
                               end as object_path
                        from murise.pictures p
                        left join murise.media_assets a on a.id = p.asset_id
                        where (p.slot = 'TEAM_ICON'
                               and p.team_id in (
                                   select t.id from murise.teams t
                                   where t.event_id = :eventId
                               ))
                           or (p.slot = 'AVATAR'
                               and p.user_id in (
                                   select ep.user_id from murise.event_participants ep
                                   where ep.event_id = :eventId
                                     and ep.user_id is not null
                               ))
                        """)
                .setParameter("eventId", eventId)
                .setParameter("bucket", SupabasePictureStorage.BUCKET)
                .getResultList();

        List<PictureRow> result = new ArrayList<>(rows.size());

        for (Object[] row : rows) {
            result.add(new PictureRow(
                    (String) row[0],
                    uuid(row[1]),
                    uuid(row[2]),
                    (String) row[3],
                    (String) row[4]
            ));
        }

        return result;
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }
}
