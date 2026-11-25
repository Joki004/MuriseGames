package com.murisegames.api.modules.Picture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SupabasePictureStorage {

    public static final String BUCKET = "murise-pictures";

    private final RestClient client;
    private final String baseUrl;

    public SupabasePictureStorage(
            @Value("${app.pictures.supabase-url}") String supabaseUrl,
            @Value("${app.pictures.service-role-key}") String serviceRoleKey
    ) {
        baseUrl = supabaseUrl.replaceAll("/+$", "") + "/storage/v1";

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));

        client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("apikey", serviceRoleKey)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + serviceRoleKey
                )
                .build();
    }

    public void upload(String path, byte[] bytes) {
        upload(path, bytes, MediaType.IMAGE_JPEG);
    }

    public void upload(String path, byte[] bytes, MediaType contentType) {
        validatePath(path);

        try {
            client.post()
                    .uri("/object/{bucket}/{path}", BUCKET, path)
                    .contentType(contentType)
                    .header("x-upsert", "false")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    public String signedUrl(String path) {
        validatePath(path);

        try {
            SignedResponse response = client.post()
                    .uri("/object/sign/{bucket}/{path}", BUCKET, path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", 300))
                    .retrieve()
                    .body(SignedResponse.class);

            String expectedPrefix = "/object/sign/" + BUCKET + "/" + path + "?";

            if (response == null
                    || response.signedURL() == null
                    || !response.signedURL().startsWith(expectedPrefix)) {
                throw unavailable();
            }

            return baseUrl + response.signedURL();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    /**
     * Signs several stored pictures in one storage call. Paths the storage could
     * not sign are left out, so callers fall back to the emoji or initials.
     */
    public Map<String, String> signedUrls(Collection<String> paths, int expiresInSeconds) {
        List<String> requested = paths.stream().distinct().toList();
        requested.forEach(this::validatePath);

        if (requested.isEmpty()) {
            return Map.of();
        }

        try {
            SignedBatchItem[] items = client.post()
                    .uri("/object/sign/{bucket}", BUCKET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", expiresInSeconds, "paths", requested))
                    .retrieve()
                    .body(SignedBatchItem[].class);

            Map<String, String> signed = new HashMap<>();

            if (items == null) {
                return signed;
            }

            for (SignedBatchItem item : items) {
                if (item == null
                        || item.error() != null
                        || item.path() == null
                        || item.signedURL() == null
                        || !requested.contains(item.path())) {
                    continue;
                }

                String expectedPrefix = "/object/sign/" + BUCKET + "/" + item.path() + "?";

                if (item.signedURL().startsWith(expectedPrefix)) {
                    signed.put(item.path(), baseUrl + item.signedURL());
                }
            }

            return signed;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    public void delete(String path) {
        validatePath(path);

        try {
            client.method(HttpMethod.DELETE)
                    .uri("/object/{bucket}", BUCKET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", List.of(path)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private void validatePath(String path) {
        if (path == null || !path.matches("[0-9a-f-]{36}\\.(jpg|png|webp|gif)")) {
            throw new IllegalStateException("Invalid stored media path.");
        }
    }

    private ResponseStatusException unavailable() {
        // Do not propagate upstream response bodies or credentials.
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Picture storage is temporarily unavailable."
        );
    }

    public record SignedResponse(String signedURL) {
    }

    public record SignedBatchItem(String error, String path, String signedURL) {
    }
}