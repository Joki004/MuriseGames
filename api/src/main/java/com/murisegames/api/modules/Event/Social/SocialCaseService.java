package com.murisegames.api.modules.Event.Social;

import com.murisegames.api.modules.Audit.AuditService;
import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
import com.murisegames.api.modules.Event.Social.SocialCaseRules.InvalidCaseException;
import com.murisegames.api.modules.Event.Social.SocialCaseTypes.*;
import com.murisegames.api.modules.User.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SocialCaseService {

    private final SocialCaseRepository caseRepository;
    private final SocialCaseVersionRepository versionRepository;
    private final SocialCaseAccessRequestRepository requestRepository;
    private final SocialRoleAssignmentRepository assignmentRepository;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PoolResponse list(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        List<SocialCase> cases = caseRepository
                .findByEventIdAndArchivedAtIsNullOrderBySortOrderAscCreatedAtAsc(eventId);

        Map<UUID, String> myLatestRequest = new HashMap<>();
        for (SocialCaseAccessRequest r : requestRepository
                .findByEventIdAndRequesterIdOrderByCreatedAtDesc(eventId, userId)) {
            myLatestRequest.putIfAbsent(r.getCaseId(), r.getStatus());
        }
        int pending = access.canApprove()
                ? (int) requestRepository.findByEventIdOrderByCreatedAtDesc(eventId).stream()
                        .filter(r -> SocialCaseAccessRequest.PENDING.equals(r.getStatus()))
                        .count()
                : 0;

        Map<UUID, SocialCaseVersion> latest = new HashMap<>();
        for (SocialCaseVersion v : versionRepository.findByEventId(eventId)) {
            latest.merge(v.getCaseId(), v,
                    (a, b) -> a.getVersionNumber() >= b.getVersionNumber() ? a : b);
        }

        Set<UUID> people = new HashSet<>();
        latest.values().forEach(v -> people.add(v.getCreatedBy()));
        cases.forEach(c -> people.add(c.getCreatedBy()));
        Map<UUID, String> names = displayNames(people);
        Set<UUID> seenByMe = casesSeenBy(eventId, userId);

        List<CaseSummary> summaries = cases.stream().map(c -> {
            SocialCaseVersion version = latest.get(c.getId());
            CaseContent content = version == null ? CaseContent.empty() : toContent(version);
            UUID editor = version == null ? c.getCreatedBy() : version.getCreatedBy();
            Instant updated = version == null ? c.getCreatedAt() : version.getCreatedAt();
            return new CaseSummary(
                    c.getId(),
                    c.getLabel(),
                    c.getSortOrder(),
                    c.getCurrentVersion(),
                    SocialCaseRules.isReady(content),
                    updated,
                    names.getOrDefault(editor, "Someone"),
                    names.getOrDefault(c.getCreatedBy(), "Someone"),
                    seenByMe.contains(c.getId()),
                    access.mayOpen(c, userId),
                    myLatestRequest.get(c.getId())
            );
        }).toList();

        return new PoolResponse(summaries, access.canApprove(), pending);
    }

    @Transactional
    public CaseDetail create(Jwt jwt, UUID eventId, CreateRequest request) {
        UUID userId = userService.extractUserId(jwt);
        lockForEditing(eventId, userId);

        SocialCase socialCase = new SocialCase();
        socialCase.setEventId(eventId);
        socialCase.setLabel(request.label().strip());
        socialCase.setSortOrder(caseRepository.maxSortOrder(eventId) + 1);
        socialCase.setCurrentVersion(0);
        socialCase.setCreatedBy(userId);
        socialCase = caseRepository.saveAndFlush(socialCase);

        markSeen(eventId, socialCase.getId(), userId);
        auditService.record(
                eventId, userId, "social_case.created", "social_case", socialCase.getId(), Map.of()
        );
        return detail(socialCase);
    }

    @Transactional
    public CaseDetail get(Jwt jwt, UUID eventId, UUID caseId) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        SocialCase socialCase = openable(access, userId,
                caseRepository.findByIdAndEventIdAndArchivedAtIsNull(caseId, eventId));
        markSeen(eventId, caseId, userId);
        return detail(socialCase);
    }

    @Transactional
    public CaseDetail save(Jwt jwt, UUID eventId, UUID caseId, SaveRequest request) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, userId);

        SocialCase socialCase = openable(access, userId, caseRepository.lockActive(eventId, caseId));
        if (socialCase.getCurrentVersion() != request.baseVersion()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Someone else saved this case while you were editing it. Reload and try again."
            );
        }

        CaseContent normalized;
        try {
            normalized = SocialCaseRules.normalize(request.content());
        } catch (InvalidCaseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        socialCase.setLabel(request.label().strip());

        CaseContent previous = currentContent(socialCase);
        boolean changed = !normalized.equals(previous);
        if (changed) {
            int nextVersion = socialCase.getCurrentVersion() + 1;
            SocialCaseVersion version = new SocialCaseVersion();
            version.setEventId(eventId);
            version.setCaseId(caseId);
            version.setVersionNumber(nextVersion);
            version.setPublicSetup(normalized.publicSetup());
            version.setDefenderGoals(normalized.defenderGoals());
            version.setDefenderConstraints(normalized.defenderConstraints());
            version.setAttackerRedFlags(normalized.attackerRedFlags());
            version.setAttackerBait(normalized.attackerBait());
            version.setJudgeRubric(SocialCaseRules.rubricToMap(normalized.judgeRubric()));
            version.setCreatedBy(userId);
            versionRepository.saveAndFlush(version);
            entityManager.refresh(version);
            socialCase.setCurrentVersion(nextVersion);
        }

        caseRepository.saveAndFlush(socialCase);
        markSeen(eventId, caseId, userId);

        auditService.record(
                eventId, userId, "social_case.saved", "social_case", caseId,
                Map.of(
                        "version", socialCase.getCurrentVersion(),
                        "newVersion", changed,
                        "ready", SocialCaseRules.isReady(normalized)
                )
        );
        return detail(socialCase);
    }

    @Transactional
    public VersionDetail version(Jwt jwt, UUID eventId, UUID caseId, int versionNumber) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        openable(access, userId, caseRepository.findByIdAndEventIdAndArchivedAtIsNull(caseId, eventId));
        SocialCaseVersion v = versionRepository
                .findByCaseIdAndVersionNumber(caseId, versionNumber)
                .orElseThrow(() -> new EntityNotFoundException("Version not found."));
        markSeen(eventId, caseId, userId);

        String author = displayNames(Set.of(v.getCreatedBy()))
                .getOrDefault(v.getCreatedBy(), "Someone");
        return new VersionDetail(v.getVersionNumber(), v.getCreatedAt(), author, toContent(v));
    }

    @Transactional
    public void archive(Jwt jwt, UUID eventId, UUID caseId) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, userId);
        SocialCase socialCase = openable(access, userId, caseRepository.lockActive(eventId, caseId));
        if (!access.canApprove() && !socialCase.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException(
                    "Only the person who wrote this case or the event creator can remove it.");
        }
        socialCase.setArchivedAt(Instant.now());
        caseRepository.saveAndFlush(socialCase);
        auditService.record(
                eventId, userId, "social_case.archived", "social_case", caseId, Map.of()
        );
    }

    @Transactional
    public RoleAssignmentView assignRole(Jwt jwt, UUID eventId, AssignRoleRequest request) {
        UUID actorId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, actorId);
        if (!access.canApprove()) {
            throw new AccessDeniedException("Only the event creator can assign active Social roles.");
        }

        SocialCase socialCase = caseRepository
                .findByIdAndEventIdAndArchivedAtIsNull(request.caseId(), eventId)
                .orElseThrow(() -> new EntityNotFoundException("Case not found."));

        int targetVersion = request.versionNumber() == null
                ? socialCase.getCurrentVersion()
                : request.versionNumber();
        if (targetVersion <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Save at least version 1 before assigning a role card.");
        }

        SocialCaseVersion version = versionRepository
                .findByCaseIdAndVersionNumber(socialCase.getId(), targetVersion)
                .orElseThrow(() -> new EntityNotFoundException("Case version not found."));

        // If a team is assigned a playable role (not AUDIENCE), exclude any case the team has seen.
        if (request.teamId() != null && request.role() != RoleCardType.AUDIENCE) {
            List<UUID> teamViewers = teamMemberIds(eventId, request.teamId());
            if (!teamViewers.isEmpty() && hasAnyViewer(socialCase.getId(), teamViewers)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A member of this team has already seen this case."
                );
            }
        }

        assignmentRepository.findByEventIdAndRoundNumberAndUserIdAndActiveTrue(
                eventId, request.roundNumber(), request.userId()
        ).ifPresent(existing -> {
            existing.setActive(false);
            assignmentRepository.saveAndFlush(existing);
        });

        SocialRoleAssignment assignment = new SocialRoleAssignment();
        assignment.setEventId(eventId);
        assignment.setFixtureId(request.fixtureId());
        assignment.setRoundNumber(request.roundNumber());
        assignment.setCaseId(socialCase.getId());
        assignment.setCaseVersionId(version.getId());
        assignment.setUserId(request.userId());
        assignment.setTeamId(request.teamId());
        assignment.setRole(request.role().name());
        assignment.setActive(true);
        assignment.setAssignedBy(actorId);
        assignment = assignmentRepository.saveAndFlush(assignment);

        auditService.record(
                eventId, actorId, "social_role.assigned", "social_case", socialCase.getId(),
                Map.of(
                        "roundNumber", request.roundNumber(),
                        "userId", request.userId().toString(),
                        "role", request.role().name(),
                        "version", targetVersion
                )
        );

        return new RoleAssignmentView(
                assignment.getId(),
                assignment.getRoundNumber(),
                assignment.getFixtureId(),
                assignment.getCaseId(),
                version.getVersionNumber(),
                assignment.getUserId(),
                assignment.getTeamId(),
                request.role(),
                assignment.isActive()
        );
    }

    /** Returns only the card sections permitted for the caller's active role in the round. */
    @Transactional(readOnly = true)
    public RoleCardView myRoleCard(Jwt jwt, UUID eventId, int roundNumber) {
        UUID userId = userService.extractUserId(jwt);
        membershipService.requireAccess(eventId, userId);

        Optional<SocialRoleAssignment> active = assignmentRepository
                .findByEventIdAndRoundNumberAndUserIdAndActiveTrue(eventId, roundNumber, userId);

        if (active.isEmpty()) {
            return new RoleCardView(
                    RoleCardType.AUDIENCE,
                    null,
                    0,
                    "",
                    "",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        SocialRoleAssignment assignment = active.get();
        SocialRoleAssignment persisted = assignment;
        SocialCase socialCase = caseRepository.findById(persisted.getCaseId())
                .orElseThrow(() -> new EntityNotFoundException("Case not found."));
        SocialCaseVersion version = versionRepository.findById(persisted.getCaseVersionId())
                .orElseThrow(() -> new EntityNotFoundException("Case version not found."));

        RoleCardType role = RoleCardType.valueOf(persisted.getRole());
        CaseContent content = toContent(version);
        return SocialCaseRules.cardForRole(
                role,
                socialCase.getId(),
                version.getVersionNumber(),
                socialCase.getLabel(),
                content,
                version.getJudgeRubric()
        );
    }

    // ---- access requests -------------------------------------------------------

    @Transactional
    public AccessRequestView requestAccess(
            Jwt jwt,
            UUID eventId,
            UUID caseId,
            AccessRequestCreate request
    ) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, userId);

        SocialCase socialCase = caseRepository
                .findByIdAndEventIdAndArchivedAtIsNull(caseId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Case not found."));
        if (access.mayOpen(socialCase, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can already open this case.");
        }
        if (requestRepository.existsByCaseIdAndRequesterIdAndStatus(
                caseId, userId, SocialCaseAccessRequest.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You already asked for this case; wait for the answer.");
        }

        SocialCaseAccessRequest row = new SocialCaseAccessRequest();
        row.setEventId(eventId);
        row.setCaseId(caseId);
        row.setRequesterId(userId);
        row.setReason(request.reason().strip());
        row.setStatus(SocialCaseAccessRequest.PENDING);
        row = requestRepository.saveAndFlush(row);
        entityManager.refresh(row);

        auditService.record(
                eventId, userId, "social_case.access_requested", "social_case", caseId,
                Map.of("requestId", row.getId().toString())
        );
        return toViews(List.of(row), Map.of(caseId, socialCase.getLabel()), userId).get(0);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestView> listRequests(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        List<SocialCaseAccessRequest> rows = access.canApprove()
                ? requestRepository.findByEventIdOrderByCreatedAtDesc(eventId)
                : requestRepository.findByEventIdAndRequesterIdOrderByCreatedAtDesc(eventId, userId);

        List<SocialCaseAccessRequest> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(
                (SocialCaseAccessRequest r) -> !SocialCaseAccessRequest.PENDING.equals(r.getStatus())));

        Map<UUID, String> labels = new HashMap<>();
        caseRepository.findAllById(
                        ordered.stream().map(SocialCaseAccessRequest::getCaseId).distinct().toList())
                .forEach(c -> labels.put(c.getId(), c.getLabel()));
        return toViews(ordered, labels, userId);
    }

    @Transactional
    public AccessRequestView decide(Jwt jwt, UUID eventId, UUID requestId, AccessDecision decision) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, userId);
        if (!access.canApprove()) {
            throw new AccessDeniedException("Only the event creator can answer access requests.");
        }

        SocialCaseAccessRequest row = requestRepository.lockById(eventId, requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found."));
        if (!SocialCaseAccessRequest.PENDING.equals(row.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This request has already been answered.");
        }

        boolean approve = Boolean.TRUE.equals(decision.approve());
        String note = decision.note() == null ? "" : decision.note().strip();
        row.setStatus(approve ? SocialCaseAccessRequest.APPROVED : SocialCaseAccessRequest.REFUSED);
        row.setDecidedBy(userId);
        row.setDecidedAt(Instant.now());
        row.setDecisionNote(note.isEmpty() ? null : note);
        requestRepository.saveAndFlush(row);

        auditService.record(
                eventId, userId,
                approve ? "social_case.access_approved" : "social_case.access_refused",
                "social_case", row.getCaseId(),
                Map.of("requestId", row.getId().toString(),
                        "requesterId", row.getRequesterId().toString())
        );

        String label = caseRepository.findById(row.getCaseId())
                .map(SocialCase::getLabel).orElse("Case");
        return toViews(List.of(row), Map.of(row.getCaseId(), label), userId).get(0);
    }

    // ---- helpers -----------------------------------------------------------------

    private CaseDetail detail(SocialCase socialCase) {
        UUID caseId = socialCase.getId();
        CaseContent content = currentContent(socialCase);

        List<SocialCaseVersion> versions = versionRepository.findByCaseIdOrderByVersionNumberDesc(caseId);
        List<UUID> viewers = viewerIds(caseId);

        Set<UUID> people = new HashSet<>(viewers);
        versions.forEach(v -> people.add(v.getCreatedBy()));
        Map<UUID, String> names = displayNames(people);

        List<VersionInfo> history = versions.stream()
                .map(v -> new VersionInfo(
                        v.getVersionNumber(),
                        v.getCreatedAt(),
                        names.getOrDefault(v.getCreatedBy(), "Someone")
                ))
                .toList();
        List<String> seenBy = viewers.stream()
                .map(id -> names.getOrDefault(id, "Someone"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return new CaseDetail(
                caseId,
                socialCase.getLabel(),
                socialCase.getCurrentVersion(),
                content,
                SocialCaseRules.isReady(content),
                SocialCaseRules.missing(content),
                history,
                seenBy
        );
    }

    private CaseContent currentContent(SocialCase socialCase) {
        if (socialCase.getCurrentVersion() == 0) {
            return CaseContent.empty();
        }
        return versionRepository
                .findByCaseIdAndVersionNumber(socialCase.getId(), socialCase.getCurrentVersion())
                .map(this::toContent)
                .orElse(CaseContent.empty());
    }

    private CaseContent toContent(SocialCaseVersion v) {
        return new CaseContent(
                v.getPublicSetup() == null ? "" : v.getPublicSetup(),
                v.getDefenderGoals() == null ? List.of() : v.getDefenderGoals(),
                v.getDefenderConstraints() == null ? List.of() : v.getDefenderConstraints(),
                v.getAttackerRedFlags() == null ? List.of() : v.getAttackerRedFlags(),
                v.getAttackerBait() == null ? List.of() : v.getAttackerBait(),
                SocialCaseRules.rubricFromMap(v.getJudgeRubric())
        );
    }

    private void markSeen(UUID eventId, UUID caseId, UUID userId) {
        entityManager.createNativeQuery("""
                        insert into murise.social_case_viewers (case_id, event_id, user_id)
                        values (:caseId, :eventId, :userId)
                        on conflict (case_id, user_id) do nothing
                        """)
                .setParameter("caseId", caseId)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> viewerIds(UUID caseId) {
        List<Object> rows = entityManager.createNativeQuery("""
                        select user_id from murise.social_case_viewers
                        where case_id = :caseId
                        order by first_seen_at
                        """)
                .setParameter("caseId", caseId)
                .getResultList();
        return rows.stream().map(r -> UUID.fromString(r.toString())).toList();
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> casesSeenBy(UUID eventId, UUID userId) {
        List<Object> rows = entityManager.createNativeQuery("""
                        select case_id from murise.social_case_viewers
                        where event_id = :eventId and user_id = :userId
                        """)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getResultList();
        Set<UUID> ids = new HashSet<>();
        rows.forEach(r -> ids.add(UUID.fromString(r.toString())));
        return ids;
    }

    @SuppressWarnings("unchecked")
    private List<UUID> teamMemberIds(UUID eventId, UUID teamId) {
        List<Object> rows = entityManager.createNativeQuery("""
                        select user_id from murise.event_participants
                        where event_id = :eventId and team_id = :teamId
                        """)
                .setParameter("eventId", eventId)
                .setParameter("teamId", teamId)
                .getResultList();
        return rows.stream().map(r -> UUID.fromString(r.toString())).toList();
    }

    private boolean hasAnyViewer(UUID caseId, List<UUID> userIds) {
        Object count = entityManager.createNativeQuery("""
                        select count(*) from murise.social_case_viewers
                        where case_id = :caseId and user_id in (:userIds)
                        """)
                .setParameter("caseId", caseId)
                .setParameter("userIds", userIds)
                .getSingleResult();
        return ((Number) count).longValue() > 0;
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, String> displayNames(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery("""
                        select id, display_name from murise.profiles where id in (:ids)
                        """)
                .setParameter("ids", ids)
                .getResultList();
        Map<UUID, String> names = new HashMap<>();
        for (Object[] row : rows) {
            names.put(UUID.fromString(row[0].toString()), String.valueOf(row[1]));
        }
        return names;
    }

    private List<AccessRequestView> toViews(
            List<SocialCaseAccessRequest> rows,
            Map<UUID, String> labels,
            UUID userId
    ) {
        Set<UUID> people = new HashSet<>();
        rows.forEach(r -> {
            people.add(r.getRequesterId());
            if (r.getDecidedBy() != null) {
                people.add(r.getDecidedBy());
            }
        });
        Map<UUID, String> names = displayNames(people);

        return rows.stream().map(r -> new AccessRequestView(
                r.getId(),
                r.getCaseId(),
                labels.getOrDefault(r.getCaseId(), "Case"),
                names.getOrDefault(r.getRequesterId(), "Someone"),
                r.getRequesterId().equals(userId),
                r.getReason(),
                r.getStatus(),
                r.getDecidedBy() == null ? null : names.getOrDefault(r.getDecidedBy(), "Someone"),
                r.getDecidedAt(),
                r.getDecisionNote(),
                r.getCreatedAt()
        )).toList();
    }

    enum Level {
        CREATOR,
        ORGANIZER,
        CASE_WRITER
    }

    record CaseAccess(Level level, Set<UUID> approvedCases) {
        boolean mayOpen(SocialCase socialCase, UUID userId) {
            return level == Level.CREATOR
                    || socialCase.getCreatedBy().equals(userId)
                    || approvedCases.contains(socialCase.getId());
        }

        boolean canApprove() {
            return level == Level.CREATOR;
        }
    }

    private CaseAccess requireCaseAccess(UUID eventId, UUID userId) {
        Set<String> roles = membershipService.requireAccess(eventId, userId);

        Level level;
        if (roles.contains("ORGANIZER")) {
            boolean creator = eventRepository.findById(eventId)
                    .map(e -> userId.equals(e.getCreatedBy()))
                    .orElse(false);
            level = creator ? Level.CREATOR : Level.ORGANIZER;
        } else if (roles.contains("CASE_WRITER")) {
            level = Level.CASE_WRITER;
        } else {
            throw new AccessDeniedException("Organizer or case writer permission required.");
        }

        Set<UUID> approved = new HashSet<>();
        for (SocialCaseAccessRequest r : requestRepository
                .findByEventIdAndRequesterIdOrderByCreatedAtDesc(eventId, userId)) {
            if (SocialCaseAccessRequest.APPROVED.equals(r.getStatus())) {
                approved.add(r.getCaseId());
            }
        }
        return new CaseAccess(level, approved);
    }

    private static SocialCase openable(
            CaseAccess access, UUID userId, Optional<SocialCase> found) {
        return found
                .filter(c -> access.mayOpen(c, userId))
                .orElseThrow(() -> new EntityNotFoundException("Case not found."));
    }

    private CaseAccess lockForEditing(UUID eventId, UUID userId) {
        requireCaseAccess(eventId, userId);
        Event event = eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));
        CaseAccess access = requireCaseAccess(eventId, userId);
        if ("completed".equals(event.getStatus()) || "archived".equals(event.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Social cases cannot be changed in a closed event."
            );
        }
        return access;
    }
}