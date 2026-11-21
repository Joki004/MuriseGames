package com.murisegames.api.modules.Picture;

import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventMediaServiceTest {

    @Mock
    private MediaAssetRepository assets;

    @Mock
    private EventMembershipService memberships;

    @Mock
    private SupabasePictureStorage storage;

    private EventMediaValidator validator;
    private EventMediaService service;

    @BeforeEach
    void setUp() {
        validator = new EventMediaValidator();
        service = new EventMediaService(assets, memberships, validator, storage);
    }

    @Test
    void acceptsValidJpegPngWebpAndGifSignatures() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] gif = "GIF89a0000".getBytes(StandardCharsets.US_ASCII);
        byte[] webp = "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII);

        assertThat(validator.detectBySignature(jpeg).mimeType()).isEqualTo("image/jpeg");
        assertThat(validator.detectBySignature(png).mimeType()).isEqualTo("image/png");
        assertThat(validator.detectBySignature(gif).mimeType()).isEqualTo("image/gif");
        assertThat(validator.detectBySignature(webp).mimeType()).isEqualTo("image/webp");
    }

    @Test
    void rejectsSvgSpoofedAsPngAndOversizedFiles() {
        MockMultipartFile svgAsPng = new MockMultipartFile(
                "file",
                "clue.png",
                "image/png",
                "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(StandardCharsets.UTF_8)
        );
        assertThatThrownBy(() -> validator.validate(svgAsPng))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JPEG, PNG, WebP, and GIF");

        byte[] tooBig = new byte[EventMediaValidator.MAX_BYTES + 1];
        tooBig[0] = (byte) 0xFF;
        tooBig[1] = (byte) 0xD8;
        tooBig[2] = (byte) 0xFF;
        MockMultipartFile oversized = new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig);

        assertThatThrownBy(() -> validator.validate(oversized))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void privateAssetBlocksOtherPlayersUntilReleased() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        MediaAsset asset = new MediaAsset();
        asset.setId(assetId);
        asset.setEventId(eventId);
        asset.setOwnerId(ownerId);
        asset.setPurpose("event_media");
        asset.setStatus("ready");
        asset.setVisibility("private");
        asset.setObjectPath(assetId + ".gif");

        when(memberships.requireAccess(eventId, otherPlayerId)).thenReturn(Set.of("PLAYER"));
        when(assets.findByIdAndEventId(assetId, eventId)).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.read(eventId, assetId, otherPlayerId))
                .isInstanceOf(AccessDeniedException.class);

        asset.setVisibility("released");
        when(storage.signedUrl(asset.getObjectPath())).thenReturn("https://example.supabase.co/signed");

        EventMediaResponse res = service.read(eventId, assetId, otherPlayerId);
        assertThat(res.signedUrl()).isEqualTo("https://example.supabase.co/signed");
    }

    @Test
    void crossEventReadReturnsNotFound() {
        UUID eventB = UUID.randomUUID();
        UUID assetFromEventA = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(memberships.requireAccess(eventB, actorId)).thenReturn(Set.of("ORGANIZER"));
        when(assets.findByIdAndEventId(assetFromEventA, eventB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(eventB, assetFromEventA, actorId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void failedUploadMarksAssetFailedForOrphanCleanup() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        byte[] gif = "GIF89a0000".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "meme.gif", "image/gif", gif);

        when(memberships.requireAccess(eventId, actorId)).thenReturn(Set.of("PLAYER"));
        doThrow(new RuntimeException("Storage down")).when(storage).upload(anyString(), any(), any());

        assertThatThrownBy(() -> service.upload(eventId, actorId, file))
                .isInstanceOf(RuntimeException.class);

        verify(assets, atLeast(2)).saveAndFlush(argThat(a ->
                "failed".equals(a.getStatus()) || "pending".equals(a.getStatus())
        ));
    }
}