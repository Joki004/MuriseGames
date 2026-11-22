package com.murisegames.api.modules.Event.Title;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "title_aliases", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class TitleAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "scene_id", nullable = false, updatable = false)
    private UUID sceneId;

    @Column(nullable = false, columnDefinition = "text")
    private String alias;

    @Column(name = "normalized_alias", nullable = false, columnDefinition = "text")
    private String normalizedAlias;
}