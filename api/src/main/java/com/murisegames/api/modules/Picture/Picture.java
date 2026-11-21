package com.murisegames.api.modules.Picture;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "pictures", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class Picture {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private PictureSlot slot;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(columnDefinition = "text")
    private String emoji;

    @Column(name = "asset_id")
    private UUID assetId;
}