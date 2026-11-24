package com.murisegames.api.modules.Event.Alibi;

import com.murisegames.api.modules.Audit.AuditService;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseRules.InvalidCaseException;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.*;
import com.murisegames.api.modules.Event.Event;
import com.murisegames.api.modules.Event.EventRepository;
import com.murisegames.api.modules.Event.Membership.EventMembershipService;
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

/**
 * GM-019: the Unified Alibi case pool.
 * Organizers and CASE_WRITERs (a helper role the organizer grants and revokes at any
 * time) create cases. Only the event creator opens any case directly; everyone else
 * opens the cases they wrote, and asks the event creator for access to any other.
 * Every saved change of content is a new immutable version; a round pins the version
 * it is played with (GM-046).
 */
@Service
@RequiredArgsConstructor
public class AlibiCaseService {

    private final AlibiCaseRepository caseRepository;
    private final AlibiCaseVersionRepository versionRepository;
    private final AlibiCaseAccessRequestRepository requestRepository;
    private final EventRepository eventRepository;
    private final EventMembershipService membershipService;
    private final UserService userService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    /**
     * The whole pool without any secret content (names and progress only), so listing
     * does not count as seeing, and anyone can ask for access to a case by name.
     */
    @Transactional(readOnly = true)
    public PoolResponse list(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        List<AlibiCase> cases = caseRepository
                .findByEventIdAndArchivedAtIsNullOrderBySortOrderAscCreatedAtAsc(eventId);

        Map<UUID, String> myLatestRequest = new HashMap<>();
        for (AlibiCaseAccessRequest r : requestRepository
                .findByEventIdAndRequesterIdOrderByCreatedAtDesc(eventId, userId)) {
            myLatestRequest.putIfAbsent(r.getCaseId(), r.getStatus());
        }
        int pending = access.canApprove()
                ? (int) requestRepository.findByEventIdOrderByCreatedAtDesc(eventId).stream()
                        .filter(r -> AlibiCaseAccessRequest.PENDING.equals(r.getStatus()))
                        .count()
                : 0;

        Map<UUID, AlibiCaseVersion> latest = new HashMap<>();
        for (AlibiCaseVersion version : versionRepository.findByEventId(eventId)) {
            latest.merge(version.getCaseId(), version,
                    (a, b) -> a.getVersionNumber() >= b.getVersionNumber() ? a : b);
        }

        Set<UUID> authors = new HashSet<>();
        latest.values().forEach(v -> authors.add(v.getCreatedBy()));
        cases.forEach(c -> authors.add(c.getCreatedBy()));
        Map<UUID, String> names = displayNames(authors);
        Set<UUID> seenByMe = casesSeenBy(eventId, userId);

        List<CaseSummary> summaries = cases.stream().map(c -> {
            AlibiCaseVersion version = latest.get(c.getId());
            CaseContent content = version == null
                    ? CaseContent.empty()
                    : AlibiCaseRules.fromMap(version.getContent());
            UUID editor = version == null ? c.getCreatedBy() : version.getCreatedBy();
            Instant updated = version == null ? c.getCreatedAt() : version.getCreatedAt();
            return new CaseSummary(
                    c.getId(),
                    c.getLabel(),
                    c.getSortOrder(),
                    c.getCurrentVersion(),
                    AlibiCaseRules.isReady(content),
                    content.questions().size(),
                    content.fixedFacts().size(),
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

        AlibiCase alibiCase = new AlibiCase();
        alibiCase.setEventId(eventId);
        alibiCase.setLabel(request.label().strip());
        alibiCase.setSortOrder(caseRepository.maxSortOrder(eventId) + 1);
        alibiCase.setCurrentVersion(0);
        alibiCase.setCreatedBy(userId);
        alibiCase = caseRepository.saveAndFlush(alibiCase);

        markSeen(eventId, alibiCase.getId(), userId);
        auditService.record(
                eventId, userId, "alibi_case.created", "alibi_case", alibiCase.getId(),
                Map.of()
        );
        return detail(alibiCase);
    }

    /** Opening a case shows its content, so the reader is recorded as having seen it. */
    @Transactional
    public CaseDetail get(Jwt jwt, UUID eventId, UUID caseId) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        AlibiCase alibiCase = openable(access, userId,
                caseRepository.findByIdAndEventIdAndArchivedAtIsNull(caseId, eventId));
        markSeen(eventId, caseId, userId);
        return detail(alibiCase);
    }

    @Transactional
    public CaseDetail save(Jwt jwt, UUID eventId, UUID caseId, SaveRequest request) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, userId);

        AlibiCase alibiCase = openable(access, userId, caseRepository.lockActive(eventId, caseId));

        if (alibiCase.getCurrentVersion() != request.baseVersion()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Someone else saved this case while you were editing it. "
                            + "Copy your changes, reload the case and apply them again."
            );
        }

        CaseContent content;
        try {
            content = AlibiCaseRules.normalize(request.content());
        } catch (InvalidCaseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        alibiCase.setLabel(request.label().strip());

        CaseContent previous = currentContent(alibiCase);
        boolean changed = !content.equals(previous);
        if (changed) {
            int next = alibiCase.getCurrentVersion() + 1;
            AlibiCaseVersion version = new AlibiCaseVersion();
            version.setEventId(eventId);
            version.setCaseId(caseId);
            version.setVersionNumber(next);
            version.setContent(AlibiCaseRules.toMap(content));
            version.setCreatedBy(userId);
            versionRepository.saveAndFlush(version);
            // created_at is set by the database; reload it so the history shows it.
            entityManager.refresh(version);
            alibiCase.setCurrentVersion(next);
        }
        caseRepository.saveAndFlush(alibiCase);
        markSeen(eventId, caseId, userId);

        // The case text itself is secret and deliberately not written to the audit log.
        auditService.record(
                eventId, userId, "alibi_case.saved", "alibi_case", caseId,
                Map.of(
                        "version", alibiCase.getCurrentVersion(),
                        "newVersion", changed,
                        "ready", AlibiCaseRules.isReady(content)
                )
        );
        return detail(alibiCase);
    }

    @Transactional
    public VersionDetail version(Jwt jwt, UUID eventId, UUID caseId, int versionNumber) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        openable(access, userId, caseRepository.findByIdAndEventIdAndArchivedAtIsNull(caseId, eventId));
        AlibiCaseVersion version = versionRepository
                .findByCaseIdAndVersionNumber(caseId, versionNumber)
                .orElseThrow(() -> new EntityNotFoundException("Version not found."));
        markSeen(eventId, caseId, userId);

        String author = displayNames(Set.of(version.getCreatedBy()))
                .getOrDefault(version.getCreatedBy(), "Someone");
        return new VersionDetail(
                version.getVersionNumber(),
                version.getCreatedAt(),
                author,
                AlibiCaseRules.fromMap(version.getContent())
        );
    }

    /** What each role would see for the current (or a given) version. */
    @Transactional
    public RolePreview preview(
            Jwt jwt,
            UUID eventId,
            UUID caseId,
            PreviewRole role,
            Integer versionNumber
    ) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        AlibiCase alibiCase = openable(access, userId,
                caseRepository.findByIdAndEventIdAndArchivedAtIsNull(caseId, eventId));

        int number = versionNumber == null ? alibiCase.getCurrentVersion() : versionNumber;
        CaseContent content = CaseContent.empty();
        if (number > 0) {
            content = versionRepository.findByCaseIdAndVersionNumber(caseId, number)
                    .map(v -> AlibiCaseRules.fromMap(v.getContent()))
                    .orElseThrow(() -> new EntityNotFoundException("Version not found."));
        }
        markSeen(eventId, caseId, userId);
        return AlibiCaseRules.view(content, role, alibiCase.getLabel(), number);
    }

    /** Removes a case from the pool. Its versions stay, so played rounds keep their history. */
    @Transactional
    public void archive(Jwt jwt, UUID eventId, UUID caseId) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, userId);
        AlibiCase alibiCase = openable(access, userId, caseRepository.lockActive(eventId, caseId));
        if (!access.canApprove() && !alibiCase.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException(
                    "Only the person who wrote this case or the event creator can remove it.");
        }
        alibiCase.setArchivedAt(Instant.now());
        caseRepository.saveAndFlush(alibiCase);
        auditService.record(
                eventId, userId, "alibi_case.archived", "alibi_case", caseId, Map.of()
        );
    }

    // ---- helpers -----------------------------------------------------------------

    private CaseDetail detail(AlibiCase alibiCase) {
        UUID caseId = alibiCase.getId();
        CaseContent content = currentContent(alibiCase);

        List<AlibiCaseVersion> versions = versionRepository.findByCaseIdOrderByVersionNumberDesc(caseId);
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
                alibiCase.getLabel(),
                alibiCase.getCurrentVersion(),
                content,
                AlibiCaseRules.isReady(content),
                AlibiCaseRules.missing(content),
                history,
                seenBy
        );
    }

    private CaseContent currentContent(AlibiCase alibiCase) {
        if (alibiCase.getCurrentVersion() == 0) {
            return CaseContent.empty();
        }
        return versionRepository
                .findByCaseIdAndVersionNumber(alibiCase.getId(), alibiCase.getCurrentVersion())
                .map(v -> AlibiCaseRules.fromMap(v.getContent()))
                .orElse(CaseContent.empty());
    }

    private void markSeen(UUID eventId, UUID caseId, UUID userId) {
        entityManager.createNativeQuery("""
                        insert into murise.alibi_case_viewers (case_id, event_id, user_id)
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
                        select user_id from murise.alibi_case_viewers
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
                        select case_id from murise.alibi_case_viewers
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

    // ---- access requests -------------------------------------------------------

    /** Ask the event creator to open a case you did not write. */
    @Transactional
    public AccessRequestView requestAccess(
            Jwt jwt,
            UUID eventId,
            UUID caseId,
            AccessRequestCreate request
    ) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, userId);

        AlibiCase alibiCase = caseRepository.findByIdAndEventIdAndArchivedAtIsNull(caseId, eventId)
                .orElseThrow(() -> new EntityNotFoundException("Case not found."));
        if (access.mayOpen(alibiCase, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can already open this case.");
        }
        if (requestRepository.existsByCaseIdAndRequesterIdAndStatus(
                caseId, userId, AlibiCaseAccessRequest.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "You already asked for this case; wait for the answer.");
        }

        AlibiCaseAccessRequest row = new AlibiCaseAccessRequest();
        row.setEventId(eventId);
        row.setCaseId(caseId);
        row.setRequesterId(userId);
        row.setReason(request.reason().strip());
        row.setStatus(AlibiCaseAccessRequest.PENDING);
        row = requestRepository.saveAndFlush(row);
        entityManager.refresh(row);

        auditService.record(
                eventId, userId, "alibi_case.access_requested", "alibi_case", caseId,
                Map.of("requestId", row.getId().toString())
        );
        return toViews(List.of(row), Map.of(caseId, alibiCase.getLabel()), userId).get(0);
    }

    /** The event creator sees every request; anyone else sees their own. */
    @Transactional(readOnly = true)
    public List<AccessRequestView> listRequests(Jwt jwt, UUID eventId) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = requireCaseAccess(eventId, userId);

        List<AlibiCaseAccessRequest> rows = access.canApprove()
                ? requestRepository.findByEventIdOrderByCreatedAtDesc(eventId)
                : requestRepository.findByEventIdAndRequesterIdOrderByCreatedAtDesc(eventId, userId);

        // Pending first, newest first inside each group.
        List<AlibiCaseAccessRequest> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(
                (AlibiCaseAccessRequest r) -> !AlibiCaseAccessRequest.PENDING.equals(r.getStatus())));

        Map<UUID, String> labels = new HashMap<>();
        caseRepository.findAllById(
                        ordered.stream().map(AlibiCaseAccessRequest::getCaseId).distinct().toList())
                .forEach(c -> labels.put(c.getId(), c.getLabel()));
        return toViews(ordered, labels, userId);
    }

    /** Only the event creator approves or refuses. */
    @Transactional
    public AccessRequestView decide(Jwt jwt, UUID eventId, UUID requestId, AccessDecision decision) {
        UUID userId = userService.extractUserId(jwt);
        CaseAccess access = lockForEditing(eventId, userId);
        if (!access.canApprove()) {
            throw new AccessDeniedException("Only the event creator can answer access requests.");
        }

        AlibiCaseAccessRequest row = requestRepository.lockById(eventId, requestId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found."));
        if (!AlibiCaseAccessRequest.PENDING.equals(row.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This request has already been answered.");
        }

        boolean approve = Boolean.TRUE.equals(decision.approve());
        String note = decision.note() == null ? "" : decision.note().strip();
        row.setStatus(approve ? AlibiCaseAccessRequest.APPROVED : AlibiCaseAccessRequest.REFUSED);
        row.setDecidedBy(userId);
        row.setDecidedAt(Instant.now());
        row.setDecisionNote(note.isEmpty() ? null : note);
        requestRepository.saveAndFlush(row);

        auditService.record(
                eventId, userId,
                approve ? "alibi_case.access_approved" : "alibi_case.access_refused",
                "alibi_case", row.getCaseId(),
                Map.of("requestId", row.getId().toString(),
                        "requesterId", row.getRequesterId().toString())
        );

        String label = caseRepository.findById(row.getCaseId())
                .map(AlibiCase::getLabel).orElse("Case");
        return toViews(List.of(row), Map.of(row.getCaseId(), label), userId).get(0);
    }

    private List<AccessRequestView> toViews(
            List<AlibiCaseAccessRequest> rows,
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

    // ---- access rules ------------------------------------------------------------

    enum Level {
        /** Created the event: opens every case and answers access requests. */
        CREATOR,
        ORGANIZER,
        CASE_WRITER
    }

    /**
     * What one person may do with the pool. Built from their current roles on every
     * request, so granting or revoking a role applies at once.
     */
    record CaseAccess(Level level, Set<UUID> approvedCases) {

        boolean mayOpen(AlibiCase alibiCase, UUID userId) {
            return level == Level.CREATOR
                    || alibiCase.getCreatedBy().equals(userId)
                    || approvedCases.contains(alibiCase.getId());
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
        for (AlibiCaseAccessRequest r : requestRepository
                .findByEventIdAndRequesterIdOrderByCreatedAtDesc(eventId, userId)) {
            if (AlibiCaseAccessRequest.APPROVED.equals(r.getStatus())) {
                approved.add(r.getCaseId());
            }
        }
        return new CaseAccess(level, approved);
    }

    /** Someone without access is told the case does not exist. */
    private static AlibiCase openable(CaseAccess access, UUID userId, Optional<AlibiCase> found) {
        return found
                .filter(c -> access.mayOpen(c, userId))
                .orElseThrow(() -> new EntityNotFoundException("Case not found."));
    }

    private CaseAccess lockForEditing(UUID eventId, UUID userId) {
        requireCaseAccess(eventId, userId);

        Event event = eventRepository.lockById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found."));
        // Checked again under the lock so a role revoked a moment ago is respected.
        CaseAccess access = requireCaseAccess(eventId, userId);

        if ("completed".equals(event.getStatus()) || "archived".equals(event.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Alibi cases cannot be changed in a closed event."
            );
        }
        return access;
    }
}
