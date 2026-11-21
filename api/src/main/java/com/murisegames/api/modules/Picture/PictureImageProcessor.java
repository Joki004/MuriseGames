package com.murisegames.api.modules.Picture;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Component
public class PictureImageProcessor {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final int MAX_SIDE = 4096;

    // Bound simultaneous image decoding within this API instance.
    private final Semaphore decodingSlots = new Semaphore(2);

    public byte[] process(MultipartFile file, PictureSlot slot) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Choose an image smaller than 5 MB."
            );
        }

        if (!decodingSlots.tryAcquire()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Image processing is busy. Please try again."
            );
        }

        try {
            byte[] input;
            try (var stream = file.getInputStream()) {
                input = stream.readNBytes(MAX_BYTES + 1);
            }

            if (input.length == 0 || input.length > MAX_BYTES) {
                throw invalid("Invalid image size.");
            }

            try (var stream = new MemoryCacheImageInputStream(
                    new ByteArrayInputStream(input)
            )) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);

                if (!readers.hasNext()) {
                    throw invalid("Upload a valid JPEG or PNG image.");
                }

                ImageReader reader = readers.next();

                try {
                    reader.setInput(stream, true, true);

                    String format = reader.getFormatName()
                            .toLowerCase(Locale.ROOT);

                    if (!Set.of("jpeg", "jpg", "png").contains(format)) {
                        throw invalid("Only JPEG and PNG images are supported.");
                    }

                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);

                    if (width < 1 || height < 1
                            || width > MAX_SIDE || height > MAX_SIDE
                            || (long) width * height > MAX_PIXELS) {
                        throw invalid("The uploaded image dimensions are too large.");
                    }

                    double expectedRatio = (double) slot.width() / slot.height();
                    double actualRatio = (double) width / height;

                    if (Math.abs(actualRatio / expectedRatio - 1) > 0.02) {
                        throw invalid(
                                slot == PictureSlot.EVENT_COVER
                                        ? "Crop the cover to 16:9."
                                        : "Crop the picture to a square."
                        );
                    }

                    BufferedImage decoded = reader.read(0);

                    if (decoded == null) {
                        throw invalid("The image could not be decoded.");
                    }

                    BufferedImage output = new BufferedImage(
                            slot.width(),
                            slot.height(),
                            BufferedImage.TYPE_INT_RGB
                    );

                    Graphics2D graphics = output.createGraphics();
                    try {
                        graphics.setColor(new Color(11, 16, 32));
                        graphics.fillRect(0, 0, output.getWidth(), output.getHeight());

                        graphics.setRenderingHint(
                                RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC
                        );

                        graphics.drawImage(
                                decoded,
                                0,
                                0,
                                output.getWidth(),
                                output.getHeight(),
                                null
                        );
                    } finally {
                        graphics.dispose();
                        decoded.flush();
                    }

                    try {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                        if (!ImageIO.write(output, "jpeg", bytes)) {
                            throw new IllegalStateException("JPEG writer unavailable.");
                        }

                        byte[] result = bytes.toByteArray();

                        if (result.length > MAX_BYTES) {
                            throw invalid("The processed image is too large.");
                        }

                        return result;
                    } finally {
                        output.flush();
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw invalid("The image is damaged or cannot be decoded.");
        } finally {
            decodingSlots.release();
        }
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}