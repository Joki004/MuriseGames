package com.murisegames.api.modules.Catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record CatalogGameCreateRequest(
        @Schema(example = "unified-alibi")
        @NotBlank(message = "Game code is required.")
        String code,

        @Schema(example = "Unified Alibi")
        @NotBlank(message = "Game name is required.")
        String name,

        @Schema(allowableValues = {"competitive", "party", "social_deduction", "rehearsal"})
        @NotBlank
        @Pattern(regexp = "competitive|party|social_deduction|rehearsal",
                message = "Kind must be competitive, party, social_deduction, or rehearsal.")
        String kind,

        @NotBlank(message = "Summary is required.")
        String summary,

        @NotBlank(message = "Rules markdown is required.")
        String rulesMarkdown,

        @NotBlank(message = "End-game scoring summary is required.")
        String endGameScoringSummary,

        @Schema(allowableValues = {"head_to_head", "team_solo", "all_teams", "multi_team_pod", "individual"})
        @NotBlank
        @Pattern(regexp = "head_to_head|team_solo|all_teams|multi_team_pod|individual",
                message = "Invalid participation mode.")
        String participationMode,

        @Schema(example = "manual_points")
        @NotBlank(message = "Scoring handler is required.")
        String scoringHandler,

        Map<String, Object> scoringConfig
) {
}