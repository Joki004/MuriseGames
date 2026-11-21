package com.murisegames.api.modules.Picture;

import com.murisegames.api.utils.CustomApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/pictures")
@RequiredArgsConstructor
public class PictureController {

    private final PictureService service;

    public record EmojiRequest(
            @NotBlank @Size(max = 32) String emoji
    ) {
    }

    @GetMapping("/{slot}/{targetId}")
    public ResponseEntity<CustomApiResponse<PictureResponse>> read(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable PictureSlot slot,
            @PathVariable UUID targetId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CustomApiResponse.success(
                        service.read(jwt, slot, targetId),
                        "Picture retrieved.",
                        200
                ));
    }

    @PutMapping("/{slot}/{targetId}/emoji")
    public ResponseEntity<CustomApiResponse<Boolean>> chooseEmoji(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable PictureSlot slot,
            @PathVariable UUID targetId,
            @Valid @RequestBody EmojiRequest request
    ) {
        service.chooseEmoji(jwt, slot, targetId, request.emoji());
        return success("Picture saved.");
    }

    @PostMapping(
            value = "/{slot}/{targetId}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<CustomApiResponse<Boolean>> upload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable PictureSlot slot,
            @PathVariable UUID targetId,
            @RequestPart("file") MultipartFile file
    ) {
        service.upload(jwt, slot, targetId, file);
        return success("Picture saved.");
    }

    @DeleteMapping("/{slot}/{targetId}")
    public ResponseEntity<CustomApiResponse<Boolean>> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable PictureSlot slot,
            @PathVariable UUID targetId
    ) {
        service.remove(jwt, slot, targetId);
        return success("Picture removed.");
    }

    private ResponseEntity<CustomApiResponse<Boolean>> success(String message) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(CustomApiResponse.success(true, message, 200));
    }
}