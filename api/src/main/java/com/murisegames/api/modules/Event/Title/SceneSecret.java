package com.murisegames.api.modules.Event.Title;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "scene_secrets", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class SceneSecret {

    @Id
    @Column(name = "scene_id")
    private UUID sceneId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "normalized_title", nullable = false, columnDefinition = "text")
    private String normalizedTitle;

    @Column(name = "answer_scope", nullable = false, columnDefinition = "text")
    private String answerScope;
}