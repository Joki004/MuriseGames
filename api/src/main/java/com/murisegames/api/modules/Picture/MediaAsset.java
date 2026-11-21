package com.murisegames.api.modules.Picture;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class MediaAsset {

    @Id
    private UUID id;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, columnDefinition = "text")
    private String purpose;

    @Column(nullable = false, columnDefinition = "text")
    private String bucket;

    @Column(name = "object_path", nullable = false, columnDefinition = "text")
    private String objectPath;

    @Column(name = "mime_type", nullable = false, columnDefinition = "text")
    private String mimeType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(nullable = false, columnDefinition = "text")
    private String status;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant createdAt;
}