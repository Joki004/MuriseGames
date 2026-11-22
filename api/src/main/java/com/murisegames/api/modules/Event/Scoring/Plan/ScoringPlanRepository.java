package com.murisegames.api.modules.Event.Scoring.Plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScoringPlanRepository
        extends JpaRepository<ScoringPlan, UUID> {
}