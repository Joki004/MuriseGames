package com.murisegames.api.modules.Event.Alibi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** GM-019 request and response shapes for Unified Alibi cases. */
public final class AlibiCaseTypes {

    private AlibiCaseTypes() {
    }

    /** What an organizer writes. Every list may be shorter while the case is a draft. */
    public record CaseContent(
            @Size(max = 2000) String brief,
            @Size(max = 30) List<@Size(max = 500) String> fixedFacts,
            @Size(max = 30) List<@Valid Question> questions,
            @Size(max = 3000) String judgeRubric
    ) {
        public static CaseContent empty() {
            return new CaseContent("", List.of(), List.of(), "");
        }
    }

    public record Question(
            @Size(max = 500) String prompt,
            @Size(max = 30) List<@Size(max = 200) String> validEquivalents,
            @Size(max = 1000) String judgeNote
    ) {
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

            /* The version the editor started from; a mismatch means someone else saved first. */
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
            int questionCount,
            int fixedFactCount,
            Instant updatedAt,
            String lastEditedBy,
            String writtenBy,
            /* True when the caller already knows this case (wrote, edited or opened it). */
            boolean seenByMe,
            /* The caller wrote it, is the event creator, or had a request approved. */
            boolean canOpen,
            /* The caller's latest request for this case: pending, approved, refused or null. */
            String myRequestStatus
    ) {
    }

    public record PoolResponse(
            List<CaseSummary> cases,
            /* Only the event creator approves access requests. */
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
            /* Members who know this case; their teams will not be given it. */
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

    public enum PreviewRole {
        AUDIENCE, PAIR, JUDGE
    }

    public record QuestionView(
            int number,
            String prompt,
            List<String> validEquivalents,
            String judgeNote
    ) {
    }

    /**
     * Exactly what one role is allowed to see. Fields a role must not see are null,
     * so the same shape can later be served to the real screens.
     */
    public record RolePreview(
            PreviewRole role,
            int versionNumber,
            String label,
            String brief,
            List<String> fixedFacts,
            List<QuestionView> questions,
            String judgeRubric
    ) {
    }
}
