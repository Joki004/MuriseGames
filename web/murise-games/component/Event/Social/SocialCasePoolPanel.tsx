"use client";

import { useCallback, useEffect, useState } from "react";
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
  const [pool, setPool] = useState<SocialPoolResponse | null>(null);
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
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setError(
          err instanceof Error
            ? err.message
            : "Failed to load Social Scenario pool.",
        );
      });
    return () => {
      cancelled = true;
    };
  }, [eventId]);

  const handleCreate = async (e: React.FormEvent) => {
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

  const handleAssignRole = async (e: React.FormEvent) => {
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

  return (
    <div className="space-y-6">
      {error && <Alert>{error}</Alert>}
      {feedback && <Alert>{feedback}</Alert>}

      <Card className="space-y-4">
        <CardHeader>
          <CardTitle>Social Scenario Case Pool</CardTitle>
          <CardDescription>
            Create versioned Social Scenario cases. Opening a case marks it seen so your team
            won&apos;t receive it during play.
          </CardDescription>
        </CardHeader>

        <form onSubmit={handleCreate} className="flex items-end gap-2 px-6">
          <div className="flex-1">
            <Input
              label="New scenario label"
              placeholder="Embassy Briefing"
              value={newLabel}
              onChange={(e) => setNewLabel(e.target.value)}
              maxLength={80}
            />
          </div>
          <Button type="submit" disabled={busy || !newLabel.trim()}>
            Create case
          </Button>
        </form>

        <div className="space-y-2 px-6 pb-6">
          {pool?.cases.map((c) => (
            <div
              key={c.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{c.label}</span>
                  <Badge>{c.ready ? "Ready" : "Draft"}</Badge>
                  <span className="text-xs text-muted-foreground">v{c.currentVersion}</span>
                  {c.seenByMe && <Badge>Seen by you</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  Written by {c.writtenBy} · Last edited by {c.lastEditedBy}
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
                    {c.myRequestStatus && <Badge>{c.myRequestStatus}</Badge>}
                    {c.myRequestStatus !== "pending" && (
                      <Button
                        variant="secondary"
                        disabled={busy}
                        onClick={() => setRequestCaseId(c.id)}
                      >
                        Request access
                      </Button>
                    )}
                  </>
                )}
              </div>

              {requestCaseId === c.id && (
                <div className="w-full pt-2 flex items-end gap-2">
                  <div className="flex-1">
                    <Input
                      label="Reason"
                      placeholder="Reason for opening this scenario..."
                      value={requestReason}
                      onChange={(e) => setRequestReason(e.target.value)}
                    />
                  </div>
                  <Button
                    disabled={busy || !requestReason.trim()}
                    onClick={() => void handleRequestAccess(c.id)}
                  >
                    Send
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
            </div>
          ))}
        </div>
      </Card>

      {selected && (
        <Card className="space-y-4 p-6">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h3 className="text-lg font-semibold">
                Editing: {selected.label} (v{selected.currentVersion})
              </h3>
              <p className="text-xs text-muted-foreground">
                Seen by: {selected.seenBy.length > 0 ? selected.seenBy.join(", ") : "None"}
              </p>
            </div>
            <Badge>{selected.ready ? "Ready to play" : "Incomplete"}</Badge>
          </div>

          {selected.missing.length > 0 && (
            <Alert>Missing before play: {selected.missing.join(" ")}</Alert>
          )}

          <div className="space-y-3">
            <Input
              label="Case Label"
              value={editLabel}
              onChange={(e) => setEditLabel(e.target.value)}
            />

            <div>
              <label className="text-xs font-medium">Public Setup (visible to all roles)</label>
              <textarea
                className="w-full rounded-md border bg-background p-2 text-sm min-h-20"
                value={publicSetup}
                onChange={(e) => setPublicSetup(e.target.value)}
              />
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-xs font-medium">Defender Goals (one per line)</label>
                <textarea
                  className="w-full rounded-md border bg-background p-2 text-sm min-h-24"
                  value={defenderGoalsText}
                  onChange={(e) => setDefenderGoalsText(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium">Defender Constraints (one per line)</label>
                <textarea
                  className="w-full rounded-md border bg-background p-2 text-sm min-h-24"
                  value={defenderConstraintsText}
                  onChange={(e) => setDefenderConstraintsText(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium">Attacker Red Flags (one per line)</label>
                <textarea
                  className="w-full rounded-md border bg-background p-2 text-sm min-h-24"
                  value={attackerRedFlagsText}
                  onChange={(e) => setAttackerRedFlagsText(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium">Attacker Bait (one per line)</label>
                <textarea
                  className="w-full rounded-md border bg-background p-2 text-sm min-h-24"
                  value={attackerBaitText}
                  onChange={(e) => setAttackerBaitText(e.target.value)}
                />
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-xs font-medium">Judge Rubric Summary</label>
                <textarea
                  className="w-full rounded-md border bg-background p-2 text-sm min-h-20"
                  value={rubricSummary}
                  onChange={(e) => setRubricSummary(e.target.value)}
                />
              </div>
              <div>
                <label className="text-xs font-medium">Judge Criteria (one per line)</label>
                <textarea
                  className="w-full rounded-md border bg-background p-2 text-sm min-h-20"
                  value={rubricCriteriaText}
                  onChange={(e) => setRubricCriteriaText(e.target.value)}
                />
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Button disabled={busy} onClick={() => void handleSave()}>
                Save new version
              </Button>
              <span className="text-xs text-muted-foreground">
                Versions:{" "}
                {selected.versions.map((v) => `v${v.versionNumber}`).join(", ") || "none"}
              </span>
            </div>
          </div>

          {pool?.canApprove && selected.currentVersion > 0 && (
            <form onSubmit={handleAssignRole} className="border-t pt-4 space-y-3">
              <h4 className="text-sm font-semibold">Assign Private Role Card</h4>
              <div className="grid gap-2 md:grid-cols-4">
                <Input
                  label="Round"
                  type="number"
                  min={1}
                  value={assignRound}
                  onChange={(e) => setAssignRound(Math.max(1, Number(e.target.value) || 1))}
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
                  onChange={(e) => setAssignRole(e.target.value as SocialRoleCardType)}
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

      {requests.length > 0 && (
        <Card className="space-y-3 p-6">
          <h3 className="text-base font-semibold">
            Access Requests ({pool?.pendingRequests ?? 0} pending)
          </h3>
          {requests.map((r) => (
            <div
              key={r.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{r.caseLabel}</span>
                  <Badge>{r.status}</Badge>
                  <span className="text-xs text-muted-foreground">by {r.requester}</span>
                </div>
                <p className="text-sm text-muted-foreground">{r.reason}</p>
              </div>
              {pool?.canApprove && r.status === "pending" && (
                <div className="flex gap-2">
                  <Button disabled={busy} onClick={() => void handleDecide(r.id, true)}>
                    Approve
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={busy}
                    onClick={() => void handleDecide(r.id, false)}
                  >
                    Refuse
                  </Button>
                </div>
              )}
            </div>
          ))}
        </Card>
      )}
    </div>
  );
}