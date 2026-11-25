"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
  Select,
} from "@/component/ui";
import {
  SocialCaseService,
  type SocialAccessRequestView,
  type SocialCaseContent,
  type SocialCaseDetail,
  type SocialPoolResponse,
  type SocialRoleCardType,
} from "@/modules/Event/Social";

interface SocialCasePoolPanelProps {
  eventId: string;
}

const emptyContent = (): SocialCaseContent => ({
  publicSetup: "",
  defenderGoals: [],
  defenderConstraints: [],
  attackerRedFlags: [],
  attackerBait: [],
  judgeRubric: { summary: "", criteria: [] },
});

const linesToArray = (value: string) =>
  value
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

const arrayToLines = (items: string[]) => items.join("\n");

export function SocialCasePoolPanel({ eventId }: SocialCasePoolPanelProps) {
  const { canManage, roles } = useEventDetail();
  const isWriter = !canManage && roles.includes("CASE_WRITER");

  const [pool, setPool] = useState<SocialPoolResponse | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [requests, setRequests] = useState<SocialAccessRequestView[]>([]);
  const [selected, setSelected] = useState<SocialCaseDetail | null>(null);
  const [newLabel, setNewLabel] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Editor state
  const [editLabel, setEditLabel] = useState("");
  const [publicSetup, setPublicSetup] = useState("");
  const [defenderGoalsText, setDefenderGoalsText] = useState("");
  const [defenderConstraintsText, setDefenderConstraintsText] = useState("");
  const [attackerRedFlagsText, setAttackerRedFlagsText] = useState("");
  const [attackerBaitText, setAttackerBaitText] = useState("");
  const [rubricSummary, setRubricSummary] = useState("");
  const [rubricCriteriaText, setRubricCriteriaText] = useState("");

  // Access request input
  const [requestCaseId, setRequestCaseId] = useState<string | null>(null);
  const [requestReason, setRequestReason] = useState("");

  // Role assignment state (creator only)
  const [assignRound, setAssignRound] = useState(1);
  const [assignUserId, setAssignUserId] = useState("");
  const [assignTeamId, setAssignTeamId] = useState("");
  const [assignRole, setAssignRole] = useState<SocialRoleCardType>("DEFENDER");

  const syncEditor = (detail: SocialCaseDetail | null) => {
    setSelected(detail);
    if (!detail) return;
    setEditLabel(detail.label);
    setPublicSetup(detail.content.publicSetup);
    setDefenderGoalsText(arrayToLines(detail.content.defenderGoals));
    setDefenderConstraintsText(arrayToLines(detail.content.defenderConstraints));
    setAttackerRedFlagsText(arrayToLines(detail.content.attackerRedFlags));
    setAttackerBaitText(arrayToLines(detail.content.attackerBait));
    setRubricSummary(detail.content.judgeRubric?.summary ?? "");
    setRubricCriteriaText(arrayToLines(detail.content.judgeRubric?.criteria ?? []));
  };

  const refresh = useCallback(async () => {
    try {
      const [poolRes, requestRes] = await Promise.all([
        SocialCaseService.list(eventId),
        SocialCaseService.listRequests(eventId),
      ]);
      setError(null);
      setPool(poolRes.data ?? null);
      setRequests(requestRes.data ?? []);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Failed to load Social Scenario pool.",
      );
    } finally {
      setLoaded(true);
    }
  }, [eventId]);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      SocialCaseService.list(eventId),
      SocialCaseService.listRequests(eventId),
    ])
      .then(([poolRes, requestRes]) => {
        if (cancelled) return;
        setError(null);
        setPool(poolRes.data ?? null);
        setRequests(requestRes.data ?? []);
        setLoaded(true);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setError(
          err instanceof Error
            ? err.message
            : "Failed to load Social Scenario pool.",
        );
        setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [eventId]);

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (!newLabel.trim()) return;
    setBusy(true);
    setError(null);
    setFeedback(null);
    try {
      const created = await SocialCaseService.create(eventId, newLabel.trim());
      setNewLabel("");
      syncEditor(created.data ?? null);
      setFeedback("Case created.");
      await refresh();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not create case.");
    } finally {
      setBusy(false);
    }
  };

  const handleOpenCase = async (caseId: string) => {
    setBusy(true);
    setError(null);
    setFeedback(null);
    try {
      const detail = await SocialCaseService.get(eventId, caseId);
      syncEditor(detail.data ?? null);
      await refresh();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not open case.");
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async () => {
    if (!selected) return;
    setBusy(true);
    setError(null);
    setFeedback(null);
    try {
      const updated = await SocialCaseService.save(eventId, selected.id, {
        label: editLabel.trim(),
        baseVersion: selected.currentVersion,
        content: {
          ...emptyContent(),
          publicSetup,
          defenderGoals: linesToArray(defenderGoalsText),
          defenderConstraints: linesToArray(defenderConstraintsText),
          attackerRedFlags: linesToArray(attackerRedFlagsText),
          attackerBait: linesToArray(attackerBaitText),
          judgeRubric: {
            summary: rubricSummary,
            criteria: linesToArray(rubricCriteriaText),
          },
        },
      });
      const nextDetail = updated.data ?? null;
      syncEditor(nextDetail);
      setFeedback(
        nextDetail
          ? `Saved (current version: v${nextDetail.currentVersion}).`
          : "Saved.",
      );
      await refresh();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save case.");
    } finally {
      setBusy(false);
    }
  };

  const handleArchive = async (caseId: string) => {
    setBusy(true);
    setError(null);
    setFeedback(null);
    try {
      await SocialCaseService.archive(eventId, caseId);
      if (selected?.id === caseId) syncEditor(null);
      setFeedback("Case removed from pool.");
      await refresh();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not archive case.");
    } finally {
      setBusy(false);
    }
  };

  const handleRequestAccess = async (caseId: string) => {
    if (!requestReason.trim()) return;
    setBusy(true);
    setError(null);
    setFeedback(null);
    try {
      await SocialCaseService.requestAccess(eventId, caseId, requestReason.trim());
      setRequestCaseId(null);
      setRequestReason("");
      setFeedback("Access request submitted.");
      await refresh();
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not submit access request.",
      );
    } finally {
      setBusy(false);
    }
  };

  const handleDecide = async (requestId: string, approve: boolean) => {
    setBusy(true);
    setError(null);
    setFeedback(null);
    try {
      await SocialCaseService.decide(eventId, requestId, approve);
      setFeedback(approve ? "Access request approved." : "Access request refused.");
      await refresh();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not decide request.");
    } finally {
      setBusy(false);
    }
  };

  const handleAssignRole = async (e: FormEvent) => {
    e.preventDefault();
    if (!selected || !assignUserId.trim()) return;
    setBusy(true);
    setError(null);
    setFeedback(null);
    try {
      await SocialCaseService.assignRole(eventId, {
        roundNumber: assignRound,
        caseId: selected.id,
        versionNumber: selected.currentVersion || undefined,
        userId: assignUserId.trim(),
        teamId: assignTeamId.trim() || null,
        role: assignRole,
      });
      setFeedback(`Assigned ${assignRole} for round ${assignRound}.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not assign role.");
    } finally {
      setBusy(false);
    }
  };

  const cases = pool?.cases ?? [];
  const canApprove = pool?.canApprove ?? canManage;
  const readyCount = cases.filter((c) => c.ready).length;

  return (
    <div className="space-y-6">
      {error && <Alert tone="error">{error}</Alert>}
      {feedback && <Alert tone="success">{feedback}</Alert>}

      <Card>
        <CardHeader>
          <CardTitle>Social cases</CardTitle>
          <CardDescription>
            {canApprove
              ? "You created the event: you can open every case and answer access requests."
              : isWriter
                ? "You are a case writer: create cases and open the ones you wrote. To open another case, ask the event creator."
                : "Create versioned Social Scenario cases and open the ones you wrote. To open another case, ask the event creator."}
          </CardDescription>
        </CardHeader>

        <Alert tone="info">
          Anyone who writes, edits or opens a case knows its details, so their
          team will never be given that case during Social Scenario.
        </Alert>

        <form onSubmit={handleCreate} className="mt-4 flex flex-wrap items-end gap-3">
          <div className="min-w-[16rem] flex-1">
            <Input
              label="New case name"
              hint="e.g. “Embassy Briefing”."
              value={newLabel}
              onChange={(e) => setNewLabel(e.target.value)}
              maxLength={80}
            />
          </div>
          <Button type="submit" disabled={busy || !newLabel.trim()}>
            Create case
          </Button>
        </form>
      </Card>

      {requests.length > 0 && (
        <Card className="space-y-4">
          <CardHeader>
            <div className="flex flex-wrap items-center gap-2">
              <CardTitle>
                {canApprove ? "Access requests" : "Your access requests"}
              </CardTitle>
              {pool && pool.pendingRequests > 0 && (
                <Badge tone="warning">{pool.pendingRequests} waiting</Badge>
              )}
            </div>
            <CardDescription>
              {canApprove
                ? "People asking to open a case they did not write. Approving means their team will never be given that case."
                : "The event creator approves or refuses each request."}
            </CardDescription>
          </CardHeader>

          <ul className="space-y-2">
            {requests.map((r) => (
              <li
                key={r.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3"
              >
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{r.caseLabel}</span>
                    <Badge
                      tone={
                        r.status === "approved"
                          ? "success"
                          : r.status === "refused"
                            ? "danger"
                            : "warning"
                      }
                    >
                      {r.status}
                    </Badge>
                    <span className="text-xs text-muted">by {r.requester}</span>
                  </div>
                  <p className="text-sm text-muted">“{r.reason}”</p>
                </div>

                {canApprove && r.status === "pending" && (
                  <div className="flex gap-2">
                    <Button
                      disabled={busy}
                      onClick={() => void handleDecide(r.id, true)}
                    >
                      Approve
                    </Button>
                    <Button
                      variant="danger"
                      disabled={busy}
                      onClick={() => void handleDecide(r.id, false)}
                    >
                      Refuse
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </Card>
      )}

      {loaded && (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle>Pool ({cases.length})</CardTitle>
              <div className="flex items-center gap-2">
                <Badge tone={readyCount > 0 ? "success" : "warning"}>
                  {readyCount} ready
                </Badge>
                <Button
                  variant="secondary"
                  disabled={busy}
                  onClick={() => void refresh()}
                >
                  Refresh
                </Button>
              </div>
            </div>
          </CardHeader>

          {cases.length === 0 ? (
            <div className="rounded-[var(--ui-radius-card)] border border-dashed border-border p-8 text-center">
              <p className="text-2xl" aria-hidden="true">
                🎭
              </p>
              <p className="mt-2 font-medium">No cases yet</p>
              <p className="mt-1 text-sm text-muted">
                Create the first case above.
              </p>
            </div>
          ) : (
            <ul className="space-y-2">
              {cases.map((c) => (
                <li
                  key={c.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3"
                >
                  <div className="min-w-0 space-y-1">
                    <p className="break-words font-medium">{c.label}</p>
                    <div className="flex flex-wrap gap-1.5">
                      <Badge tone={c.ready ? "success" : "warning"}>
                        {c.ready ? "Ready" : "Draft"}
                      </Badge>
                      {c.currentVersion > 0 && (
                        <Badge>v{c.currentVersion}</Badge>
                      )}
                      {c.seenByMe && <Badge tone="accent">You know it</Badge>}
                    </div>
                    <p className="text-xs text-muted">
                      Written by {c.writtenBy} · last edit by {c.lastEditedBy}
                    </p>
                  </div>

                  <div className="flex items-center gap-2">
                    {c.canOpen ? (
                      <>
                        <Button
                          variant="secondary"
                          disabled={busy}
                          onClick={() => void handleOpenCase(c.id)}
                        >
                          Open
                        </Button>
                        <Button
                          variant="danger"
                          disabled={busy}
                          onClick={() => void handleArchive(c.id)}
                        >
                          Remove
                        </Button>
                      </>
                    ) : (
                      <>
                        {c.myRequestStatus && (
                          <Badge
                            tone={
                              c.myRequestStatus === "approved"
                                ? "success"
                                : c.myRequestStatus === "refused"
                                  ? "danger"
                                  : "warning"
                            }
                          >
                            {c.myRequestStatus}
                          </Badge>
                        )}
                        {c.myRequestStatus !== "pending" && (
                          <Button
                            variant="secondary"
                            disabled={busy}
                            onClick={() => {
                              setRequestCaseId(c.id);
                              setRequestReason("");
                            }}
                          >
                            🔒 Ask to open
                          </Button>
                        )}
                      </>
                    )}
                  </div>

                  {requestCaseId === c.id && (
                    <div className="mt-2 flex w-full flex-wrap items-end gap-2 border-t border-border pt-3">
                      <div className="min-w-[14rem] flex-1">
                        <Input
                          label="Why do you need to open it?"
                          placeholder="Reason for opening this scenario..."
                          value={requestReason}
                          onChange={(e) => setRequestReason(e.target.value)}
                        />
                      </div>
                      <Button
                        disabled={busy || !requestReason.trim()}
                        onClick={() => void handleRequestAccess(c.id)}
                      >
                        Send request
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setRequestCaseId(null);
                          setRequestReason("");
                        }}
                      >
                        Cancel
                      </Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      {selected && (
        <Card className="space-y-4">
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div>
                <CardTitle>
                  Editing: {selected.label} (v{selected.currentVersion})
                </CardTitle>
                <CardDescription>
                  Seen by:{" "}
                  {selected.seenBy.length > 0
                    ? selected.seenBy.join(", ")
                    : "None"}
                </CardDescription>
              </div>
              <Badge tone={selected.ready ? "success" : "warning"}>
                {selected.ready ? "Ready to play" : "Draft"}
              </Badge>
            </div>
          </CardHeader>

          {selected.missing.length > 0 && (
            <Alert tone="info">
              Missing before play: {selected.missing.join(" ")}
            </Alert>
          )}

          <div className="space-y-4">
            <Input
              label="Case label"
              value={editLabel}
              onChange={(e) => setEditLabel(e.target.value)}
            />

            <div>
              <label className="text-xs font-medium">
                Public setup (visible to all roles)
              </label>
              <textarea
                className="mt-1 min-h-20 w-full rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-2 text-sm"
                value={publicSetup}
                onChange={(e) => setPublicSetup(e.target.value)}
              />
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-xs font-medium">
                  Defender goals (one per line)
                </label>
                <textarea
                  className="mt-1 min-h-24 w-full rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-2 text-sm"
                  value={defenderGoalsText}
                  onChange={(e) => setDefenderGoalsText(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium">
                  Defender constraints (one per line)
                </label>
                <textarea
                  className="mt-1 min-h-24 w-full rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-2 text-sm"
                  value={defenderConstraintsText}
                  onChange={(e) => setDefenderConstraintsText(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium">
                  Attacker red flags (one per line)
                </label>
                <textarea
                  className="mt-1 min-h-24 w-full rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-2 text-sm"
                  value={attackerRedFlagsText}
                  onChange={(e) => setAttackerRedFlagsText(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium">
                  Attacker bait (one per line)
                </label>
                <textarea
                  className="mt-1 min-h-24 w-full rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-2 text-sm"
                  value={attackerBaitText}
                  onChange={(e) => setAttackerBaitText(e.target.value)}
                />
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-xs font-medium">
                  Judge rubric summary
                </label>
                <textarea
                  className="mt-1 min-h-20 w-full rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-2 text-sm"
                  value={rubricSummary}
                  onChange={(e) => setRubricSummary(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium">
                  Judge criteria (one per line)
                </label>
                <textarea
                  className="mt-1 min-h-20 w-full rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-2 text-sm"
                  value={rubricCriteriaText}
                  onChange={(e) => setRubricCriteriaText(e.target.value)}
                />
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-3 border-t border-border pt-4">
              <Button disabled={busy} onClick={() => void handleSave()}>
                Save new version
              </Button>
              <Button
                variant="secondary"
                disabled={busy}
                onClick={() => syncEditor(null)}
              >
                Close editor
              </Button>
              <span className="text-xs text-muted">
                Versions:{" "}
                {selected.versions
                  .map((v) => `v${v.versionNumber}`)
                  .join(", ") || "none"}
              </span>
            </div>
          </div>

          {canApprove && selected.currentVersion > 0 && (
            <form
              onSubmit={handleAssignRole}
              className="space-y-3 border-t border-border pt-4"
            >
              <h4 className="text-sm font-semibold">Assign Private Role Card</h4>
              <div className="grid gap-2 md:grid-cols-4">
                <Input
                  label="Round"
                  type="number"
                  min={1}
                  value={assignRound}
                  onChange={(e) =>
                    setAssignRound(Math.max(1, Number(e.target.value) || 1))
                  }
                />
                <Input
                  label="Target User UUID"
                  placeholder="User UUID"
                  value={assignUserId}
                  onChange={(e) => setAssignUserId(e.target.value)}
                />
                <Input
                  label="Team UUID (optional)"
                  placeholder="Team UUID"
                  value={assignTeamId}
                  onChange={(e) => setAssignTeamId(e.target.value)}
                />
                <Select
                  label="Role"
                  value={assignRole}
                  onChange={(e) =>
                    setAssignRole(e.target.value as SocialRoleCardType)
                  }
                >
                  <option value="DEFENDER">DEFENDER</option>
                  <option value="ATTACKER">ATTACKER</option>
                  <option value="JUDGE">JUDGE</option>
                  <option value="AUDIENCE">AUDIENCE</option>
                </Select>
              </div>
              <Button type="submit" disabled={busy || !assignUserId.trim()}>
                Assign role card
              </Button>
            </form>
          )}
        </Card>
      )}
    </div>
  );
}