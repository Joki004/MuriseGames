package com.murisegames.api.modules.Picture;

import com.murisegames.api.modules.User.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PictureService {

    private final UserService users;
    private final PictureTransactions transactions;
    private final PictureImageProcessor processor;
    private final SupabasePictureStorage storage;

    public PictureResponse read(
            Jwt jwt,
            PictureSlot slot,
            UUID targetId
    ) {
        UUID actorId = users.extractUserId(jwt);
        var selection = transactions.read(actorId, slot, targetId);

        String url = selection.objectPath() == null
                ? null
                : storage.signedUrl(selection.objectPath());

        return new PictureResponse(
                selection.slot(),
                selection.emoji(),
                url,
                selection.canEdit()
        );
    }

    public void upload(
            Jwt jwt,
            PictureSlot slot,
            UUID targetId,
            MultipartFile file
    ) {
        UUID actorId = users.extractUserId(jwt);

        transactions.checkWrite(actorId, slot, targetId);

        byte[] normalized = processor.process(file, slot);

        MediaAsset asset = transactions.reserve(
                actorId,
                slot,
                targetId,
                normalized.length
        );

        try {
            storage.upload(asset.getObjectPath(), normalized);
            transactions.attach(actorId, slot, targetId, asset.getId());
        } catch (RuntimeException exception) {
            try {
                transactions.markFailed(asset.getId());
            } catch (RuntimeException ignored) {
                // The durable pending row is also eligible for stale cleanup.
                log.warn("Could not mark picture upload failed; assetId={}",
                        asset.getId());
            }

            throw exception;
        }
    }

    public void chooseEmoji(
            Jwt jwt,
            PictureSlot slot,
            UUID targetId,
            String emoji
    ) {
        transactions.chooseEmoji(
                users.extractUserId(jwt),
                slot,
                targetId,
                emoji
        );
    }

    public void remove(Jwt jwt, PictureSlot slot, UUID targetId) {
        transactions.chooseEmoji(
                users.extractUserId(jwt),
                slot,
                targetId,
                null
        );
    }
}