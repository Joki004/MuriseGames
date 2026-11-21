package com.murisegames.api.modules.Picture;

import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Team.Team;
import com.murisegames.api.modules.Event.Team.TeamRepository;
import com.murisegames.api.modules.User.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PictureTransactions {

    private static final Set<String> EMOJIS = Set.of(
            "🎲", "🎬", "🎵", "🏆",
            "🚀", "🌙", "⭐", "🔥",
            "🌈", "🦊", "🐼", "🐉",
            "🧩", "🎭", "👑", "💎"
    );

    private final PictureRepository pictures;
    private final MediaAssetRepository assets;
    private final EventRepository events;
    private final TeamRepository teams;
    private final EventMembershipService memberships;
    private final EntityManager entityManager;
    private final SupabasePictureStorage storage;

    public record Selection(
            PictureSlot slot,
            String emoji,
            String objectPath,
            boolean canEdit
    ) {
    }

    private record Target(
            PictureSlot slot,
            UUID userId,
            UUID eventId,
            UUID teamId,
            boolean canEdit
    ) {
    }

    @Transactional(readOnly = true)
    public Selection read(UUID actorId, PictureSlot slot, UUID targetId) {
        Target target = authorize(actorId, slot, targetId, false);
        Picture picture = find(target).orElse(null);

        if (picture == null) {
            return new Selection(slot, null, null, target.canEdit());
        }

        String path = null;

        if (picture.getAssetId() != null) {
            MediaAsset asset = assets.findById(picture.getAssetId())
                    .orElseThrow(() ->
                            new EntityNotFoundException("Picture asset not found.")
                    );

            if (!"picture".equals(asset.getPurpose())
                    || !"ready".equals(asset.getStatus())
                    || !SupabasePictureStorage.BUCKET.equals(asset.getBucket())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This picture is temporarily unavailable."
                );
            }

            path = asset.getObjectPath();
        }

        return new Selection(
                slot,
                picture.getEmoji(),
                path,
                target.canEdit()
        );
    }

    @Transactional
    public void checkWrite(UUID actorId, PictureSlot slot, UUID targetId) {
        authorize(actorId, slot, targetId, true);
    }

    @Transactional
    public MediaAsset reserve(
            UUID actorId,
            PictureSlot slot,
            UUID targetId,
            int byteSize
    ) {
        Target target = authorize(actorId, slot, targetId, true);

        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setEventId(target.eventId());
        asset.setOwnerId(actorId);
        asset.setPurpose("picture");
        asset.setBucket(SupabasePictureStorage.BUCKET);
        asset.setObjectPath(asset.getId() + ".jpg");
        asset.setMimeType("image/jpeg");
        asset.setByteSize(byteSize);
        asset.setStatus("pending");

        MediaAsset saved = assets.saveAndFlush(asset);
        entityManager.refresh(saved);

        return saved;
    }

    @Transactional
    public void attach(
            UUID actorId,
            PictureSlot slot,
            UUID targetId,
            UUID assetId
    ) {
        // Re-check permissions after the external upload.
        Target target = authorize(actorId, slot, targetId, true);

        MediaAsset asset = assets.lockById(assetId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Picture asset not found.")
                );

        if (!"picture".equals(asset.getPurpose())
                || !"pending".equals(asset.getStatus())
                || !actorId.equals(asset.getOwnerId())
                || !Objects.equals(target.eventId(), asset.getEventId())
                || !SupabasePictureStorage.BUCKET.equals(asset.getBucket())
                || asset.getCreatedAt().isBefore(
                Instant.now().minus(15, ChronoUnit.MINUTES)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This upload cannot be attached. Please upload again."
            );
        }

        Picture picture = find(target).orElseGet(() -> newPicture(target));

        retire(picture.getAssetId());

        asset.setStatus("ready");
        picture.setEmoji(null);
        picture.setAssetId(asset.getId());

        pictures.saveAndFlush(picture);
    }

    @Transactional
    public void chooseEmoji(
            UUID actorId,
            PictureSlot slot,
            UUID targetId,
            String emoji
    ) {
        Target target = authorize(actorId, slot, targetId, true);

        if (emoji != null && (
                slot == PictureSlot.EVENT_COVER || !EMOJIS.contains(emoji)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose one of the supported emojis."
            );
        }

        Picture picture = find(target).orElseGet(() -> newPicture(target));

        retire(picture.getAssetId());

        picture.setAssetId(null);
        picture.setEmoji(emoji);

        pictures.saveAndFlush(picture);
    }

    @Transactional
    public void markFailed(UUID assetId) {
        assets.lockById(assetId).ifPresent(asset -> {
            if ("picture".equals(asset.getPurpose())
                    && "pending".equals(asset.getStatus())) {
                asset.setStatus("failed");
            }
        });
    }

    @Transactional(readOnly = true)
    public List<UUID> cleanupCandidates() {
        return assets.findCleanupCandidates(
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
    }

    @Transactional
    public void cleanup(UUID assetId) {
        MediaAsset asset = assets.lockById(assetId).orElse(null);

        if (asset == null || !"picture".equals(asset.getPurpose())) {
            return;
        }

        boolean stalePending = "pending".equals(asset.getStatus())
                && asset.getCreatedAt().isBefore(
                Instant.now().minus(1, ChronoUnit.HOURS)
        );

        boolean eligible = stalePending
                || "failed".equals(asset.getStatus())
                || "delete_pending".equals(asset.getStatus());

        if (!eligible || pictures.existsByAssetId(assetId)) {
            return;
        }

        // A failure leaves the row eligible for a later retry.
        storage.delete(asset.getObjectPath());
        asset.setStatus("deleted");
    }

    private Target authorize(
            UUID actorId,
            PictureSlot slot,
            UUID targetId,
            boolean write
    ) {
        if (slot == PictureSlot.AVATAR) {
            if (write && !actorId.equals(targetId)) {
                throw new AccessDeniedException(
                        "You can change only your own avatar."
                );
            }

            if (!actorId.equals(targetId)
                    && !pictures.sharesActiveEvent(actorId, targetId)) {
                throw new EntityNotFoundException("Picture not found.");
            }

            User user = write
                    ? entityManager.find(
                    User.class,
                    targetId,
                    LockModeType.PESSIMISTIC_WRITE
            )
                    : entityManager.find(User.class, targetId);

            if (user == null) {
                throw new EntityNotFoundException("Profile not found.");
            }

            return new Target(
                    slot, targetId, null, null, actorId.equals(targetId)
            );
        }

        UUID eventId = targetId;
        UUID teamId = null;

        if (slot == PictureSlot.TEAM_ICON) {
            Team team = teams.findById(targetId)
                    .orElseThrow(() ->
                            new EntityNotFoundException("Team not found.")
                    );

            eventId = team.getEventId();
            teamId = team.getId();
        }

        Set<String> roles = memberships.requireAccess(eventId, actorId);

        if (write) {
            memberships.requireOrganizer(eventId, actorId);

            Event event = events.lockById(eventId)
                    .orElseThrow(() ->
                            new EntityNotFoundException("Event not found.")
                    );

            memberships.requireOrganizer(event.getId(), actorId);
        }

        return new Target(
                slot,
                null,
                eventId,
                teamId,
                roles.contains("ORGANIZER")
        );
    }

    private Optional<Picture> find(Target target) {
        return switch (target.slot()) {
            case AVATAR ->
                    pictures.findBySlotAndUserId(
                            target.slot(), target.userId()
                    );
            case TEAM_ICON ->
                    pictures.findBySlotAndTeamId(
                            target.slot(), target.teamId()
                    );
            case EVENT_ICON, EVENT_COVER ->
                    pictures.findBySlotAndEventId(
                            target.slot(), target.eventId()
                    );
        };
    }

    private Picture newPicture(Target target) {
        Picture picture = new Picture();
        picture.setSlot(target.slot());
        picture.setUserId(target.userId());
        picture.setEventId(target.eventId());
        picture.setTeamId(target.teamId());
        return picture;
    }

    private void retire(UUID assetId) {
        if (assetId == null) return;

        MediaAsset asset = assets.lockById(assetId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Picture asset not found.")
                );

        if (!"picture".equals(asset.getPurpose())) {
            throw new IllegalStateException("Unexpected picture asset purpose.");
        }

        asset.setStatus("delete_pending");
    }
}