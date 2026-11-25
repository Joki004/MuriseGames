package com.murisegames.api.modules.Event.Social;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SocialCaseTypes {

    private SocialCaseTypes() {
    }

    public record JudgeRubric(
            @Size(max = 2000) String summary,
            @Size(max = 20) List<@Size(max = 400) String> criteria
    ) {
        public static JudgeRubric empty() {
            return new JudgeRubric("", List.of());
        }
    }

    public record CaseContent(
            @Size(max = 2000) String publicSetup,
            @Size(max = 20) List<@Size(max = 400) String> defenderGoals,
            @Size(max = 20) List<@Size(max = 400) String> defenderConstraints,
            @Size(max = 20) List<@Size(max = 400) String> attackerRedFlags,
            @Size(max = 20) List<@Size(max = 400) String> attackerBait,
            @Valid JudgeRubric judgeRubric
    ) {
        public static CaseContent empty() {
            return new CaseContent(
                    "", List.of(), List.of(), List.of(), List.of(), JudgeRubric.empty());
        }
    }

    public record CreateRequest(
            @NotBlank
            @Size(max = 80, message = "A case name can have at most 80 characters.")
            String label
    ) {
    }

    public record SaveRequest(
            @NotBlank
            @Size(max = 80, message = "A case name can have at most 80 characters.")
            String label,

            @NotNull @Min(0) Integer baseVersion,

            @NotNull @Valid CaseContent content
    ) {
    }

    public record CaseSummary(
            UUID id,
            String label,
            int sortOrder,
            int currentVersion,
            boolean ready,
            Instant updatedAt,
            String lastEditedBy,
            String writtenBy,
            boolean seenByMe,
            boolean canOpen,
            String myRequestStatus
    ) {
    }

    public record PoolResponse(
            List<CaseSummary> cases,
            boolean canApprove,
            int pendingRequests
    ) {
    }

    public record AccessRequestCreate(
            @NotBlank
            @Size(max = 500, message = "Keep the reason under 500 characters.")
            String reason
    ) {
    }

    public record AccessDecision(
            @NotNull Boolean approve,
            @Size(max = 500, message = "Keep the note under 500 characters.")
            String note
    ) {
    }

    public record AccessRequestView(
            UUID id,
            UUID caseId,
            String caseLabel,
            String requester,
            boolean mine,
            String reason,
            String status,
            String decidedBy,
            Instant decidedAt,
            String decisionNote,
            Instant createdAt
    ) {
    }

    public record VersionInfo(
            int versionNumber,
            Instant createdAt,
            String createdBy
    ) {
    }

    public record CaseDetail(
            UUID id,
            String label,
            int currentVersion,
            CaseContent content,
            boolean ready,
            List<String> missing,
            List<VersionInfo> versions,
            List<String> seenBy
    ) {
    }

    public record VersionDetail(
            int versionNumber,
            Instant createdAt,
            String createdBy,
            CaseContent content
    ) {
    }

    public enum RoleCardType {
        DEFENDER,
        ATTACKER,
        JUDGE,
        AUDIENCE
    }

    public record AssignRoleRequest(
            @NotNull @Min(1) Integer roundNumber,
            UUID fixtureId,
            @NotNull UUID caseId,
            Integer versionNumber,
            @NotNull UUID userId,
            UUID teamId,
            @NotNull RoleCardType role
    ) {
    }

    public record RoleAssignmentView(
            UUID id,
            int roundNumber,
            UUID fixtureId,
            UUID caseId,
            int versionNumber,
            UUID userId,
            UUID teamId,
            RoleCardType role,
            boolean active
    ) {
    }

    /**
     * Role-scoped card payload: disallowed fields stay null so callers never receive
     * secret sections belonging to another role.
     */
    public record RoleCardView(
            RoleCardType role,
            UUID caseId,
            int versionNumber,
            String label,
            String publicSetup,
            List<String> defenderGoals,
            List<String> defenderConstraints,
            List<String> attackerRedFlags,
            List<String> attackerBait,
            JudgeRubric judgeRubric,
            Map<String, Object> rawJudgeRubric
    ) {
    }
}