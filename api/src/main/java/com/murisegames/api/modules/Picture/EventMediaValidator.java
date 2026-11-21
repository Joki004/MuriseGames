package com.murisegames.api.modules.Picture;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class EventMediaValidator {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    public record ValidatedMedia(
            byte[] bytes,
            String mimeType,
            String extension,
            MediaType mediaType
    ) {
    }

    public ValidatedMedia validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Choose an image smaller than 5 MB."
            );
        }

        byte[] bytes;
        try (var stream = file.getInputStream()) {
            bytes = stream.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw badRequest("Could not read uploaded file.");
        }

        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Choose an image smaller than 5 MB."
            );
        }

        return detectBySignature(bytes);
    }

    public ValidatedMedia detectBySignature(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return new ValidatedMedia(bytes, "image/jpeg", "jpg", MediaType.IMAGE_JPEG);
        }

        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return new ValidatedMedia(bytes, "image/png", "png", MediaType.IMAGE_PNG);
        }

        if (bytes.length >= 6) {
            String header6 = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(header6) || "GIF89a".equals(header6)) {
                return new ValidatedMedia(bytes, "image/gif", "gif", MediaType.IMAGE_GIF);
            }
        }

        if (bytes.length >= 12) {
            String riff = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
            String webp = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
                return new ValidatedMedia(
                        bytes,
                        "image/webp",
                        "webp",
                        MediaType.parseMediaType("image/webp")
                );
            }
        }

        throw badRequest("Only valid JPEG, PNG, WebP, and GIF images are allowed.");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}