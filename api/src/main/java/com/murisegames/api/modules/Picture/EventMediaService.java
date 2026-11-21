package com.murisegames.api.modules.Picture;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventMediaService {

    private final MediaAssetRepository assets;
    private final EventMembershipService memberships;
    private final EventMediaValidator validator;
    private final SupabasePictureStorage storage;

    @Transactional
    public EventMediaResponse upload(
            UUID eventId,
            UUID actorId,
            MultipartFile file
    ) {
        memberships.requireAccess(eventId, actorId);
        var validated = validator.validate(file);

        UUID assetId = UUID.randomUUID();
        String path = assetId + "." + validated.extension();

        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setEventId(eventId);
        asset.setOwnerId(actorId);
        asset.setPurpose("event_media");
        asset.setBucket(SupabasePictureStorage.BUCKET);
        asset.setObjectPath(path);
        asset.setMimeType(validated.mimeType());
        asset.setByteSize(validated.bytes().length);
        asset.setVisibility("private");
        asset.setStatus("pending");
        assets.saveAndFlush(asset);

        try {
            storage.upload(path, validated.bytes(), validated.mediaType());
            asset.setStatus("ready");
            assets.saveAndFlush(asset);
        } catch (RuntimeException ex) {
            asset.setStatus("failed");
            assets.saveAndFlush(asset);
            throw ex;
        }

        String signedUrl = storage.signedUrl(asset.getObjectPath());
        return toResponse(asset, signedUrl);
    }

    @Transactional(readOnly = true)
    public EventMediaResponse read(UUID eventId, UUID assetId, UUID actorId) {
        Set<String> roles = memberships.requireAccess(eventId, actorId);

        MediaAsset asset = assets.findByIdAndEventId(assetId, eventId)
                .filter(a -> "event_media".equals(a.getPurpose()) && "ready".equals(a.getStatus()))
                .orElseThrow(() -> new EntityNotFoundException("Media asset not found."));

        boolean isOrganizer = roles.contains("ORGANIZER");
        boolean isOwner = actorId.equals(asset.getOwnerId());

        if ("private".equals(asset.getVisibility()) && !isOwner && !isOrganizer) {
            throw new AccessDeniedException("This media asset has not been released yet.");
        }

        String signedUrl = storage.signedUrl(asset.getObjectPath());
        return toResponse(asset, signedUrl);
    }

    @Transactional
    public EventMediaResponse release(UUID eventId, UUID assetId, UUID actorId) {
        memberships.requireOrganizer(eventId, actorId);

        MediaAsset asset = assets.findByIdAndEventId(assetId, eventId)
                .filter(a -> "event_media".equals(a.getPurpose()) && "ready".equals(a.getStatus()))
                .orElseThrow(() -> new EntityNotFoundException("Media asset not found."));

        asset.setVisibility("released");
        assets.saveAndFlush(asset);

        return toResponse(asset, storage.signedUrl(asset.getObjectPath()));
    }

    @Transactional
    public void delete(UUID eventId, UUID assetId, UUID actorId) {
        Set<String> roles = memberships.requireAccess(eventId, actorId);

        MediaAsset asset = assets.findByIdAndEventId(assetId, eventId)
                .filter(a -> "event_media".equals(a.getPurpose()))
                .orElseThrow(() -> new EntityNotFoundException("Media asset not found."));

        if (!actorId.equals(asset.getOwnerId()) && !roles.contains("ORGANIZER")) {
            throw new AccessDeniedException("Only the owner or an organizer can delete this asset.");
        }

        asset.setStatus("delete_pending");
        assets.saveAndFlush(asset);
    }

    private EventMediaResponse toResponse(MediaAsset asset, String signedUrl) {
        return new EventMediaResponse(
                asset.getId(),
                asset.getEventId(),
                asset.getOwnerId(),
                asset.getMimeType(),
                asset.getByteSize(),
                asset.getVisibility(),
                signedUrl,
                asset.getCreatedAt()
        );
    }
}