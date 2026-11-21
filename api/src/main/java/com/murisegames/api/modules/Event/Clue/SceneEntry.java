package com.murisegames.api.modules.Event.Clue;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "scene_entries", schema = "murise")
@Getter
@Setter
@NoArgsConstructor
public class SceneEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;
}