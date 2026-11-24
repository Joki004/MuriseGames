"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Dialog,
  EmptyState,
  Input,
  LoadingState,
  Textarea,
} from "@/component/ui";
import {
  ALIBI_LABEL_MAX,
  ALIBI_QUESTION_COUNT,
  ALIBI_REASON_MAX,
  AlibiController,
  type AlibiCaseSummary,
  type AlibiPool,
} from "@/modules/Event/Alibi";
import { AlibiAccessRequests } from "./AlibiAccessRequests";
import { AlibiCaseEditor } from "./AlibiCaseEditor";

interface Loaded {
  tick: number;
  pool: AlibiPool | null;
  error: string | null;
}

export function AlibiCasePool() {
  const { event, canManage, roles } = useEventDetail();
  const isWriter = !canManage && roles.includes("CASE_WRITER");
  const allowed = canManage || isWriter;

  const [tick, setTick] = useState(0);
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [openCaseId, setOpenCaseId] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState<AlibiCaseSummary | null>(null);

  const [asking, setAsking] = useState<AlibiCaseSummary | null>(null);
  const [reason, setReason] = useState("");
  const [sending, setSending] = useState(false);
  const [askError, setAskError] = useState<string | null>(null);

  const [newLabel, setNewLabel] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  useEffect(() => {
    if (!allowed) return;
    let cancelled = false;
    AlibiController.list(event.id)
      .then((pool) => {
        if (!cancelled) setLoaded({ tick, pool, error: null });
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setLoaded({
            tick,
            pool: null,
            error: err instanceof Error ? err.message : "Could not load the cases.",
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [allowed, event.id, tick]);

  const refresh = () => setTick((t) => t + 1);

  async function create(e: FormEvent) {
    e.preventDefault();
    setCreating(true);
    setCreateError(null);
    try {
      const created = await AlibiController.create(event.id, newLabel);
      setNewLabel("");
      refresh();
      setOpenCaseId(created.id);
    } catch (err: unknown) {
      setCreateError(
        err instanceof Error ? err.message : "Could not create the case.",
      );
    } finally {
      setCreating(false);
    }
  }

  async function sendRequest() {
    if (!asking) return;
    setSending(true);
    setAskError(null);
    try {
      await AlibiController.requestAccess(event.id, asking.id, reason);
      setAsking(null);
      setReason("");
      refresh();
    } catch (err: unknown) {
      setAskError(err instanceof Error ? err.message : "Could not send the request.");
    } finally {
      setSending(false);
    }
  }

  function requestOpen(summary: AlibiCaseSummary) {
    // Opening a case you have not seen yet means your team can no longer get it.
    if (summary.seenByMe) {
      setOpenCaseId(summary.id);
    } else {
      setConfirmOpen(summary);
    }
  }

  if (!allowed) {
    return (
      <Alert tone="info">
        Only organizers and case writers can see the Alibi case pool.
      </Alert>
    );
  }

  if (openCaseId) {
    return (
      <AlibiCaseEditor
        eventId={event.id}
        caseId={openCaseId}
        onClose={() => setOpenCaseId(null)}
        onChanged={refresh}
      />
    );
  }

  const pool = loaded?.pool ?? null;
  const cases = pool?.cases ?? [];
  const canApprove = pool?.canApprove ?? false;
  const readyCount = cases.filter((c) => c.ready).length;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Alibi cases</CardTitle>
          <CardDescription>
            {canApprove
              ? "You created the event: you can open every case and answer access requests."
              : isWriter
                ? "You are a case writer: create cases and open the ones you wrote. To open another case, ask the event creator."
                : "Create cases and open the ones you wrote. To open another case, ask the event creator."}
          </CardDescription>
        </CardHeader>

        <Alert tone="info">
          Anyone who writes, edits or opens a case knows its answers, so their
          team will never be given that case during the Alibi game.
        </Alert>

        <form onSubmit={create} className="mt-4 flex flex-wrap items-end gap-3">
          <div className="min-w-[16rem] flex-1">
            <Input
              label="New case name"
              hint="Only organizers see the name, e.g. “Dinner at Luigi's”."
              maxLength={ALIBI_LABEL_MAX}
              value={newLabel}
              onChange={(e) => setNewLabel(e.target.value)}
            />
          </div>
          <Button
            type="submit"
            loading={creating}
            loadingText="Creating…"
            disabled={!newLabel.trim()}
          >
            Create case
          </Button>
        </form>
        {createError && (
          <div className="mt-3">
            <Alert tone="error">{createError}</Alert>
          </div>
        )}
      </Card>

      {!loaded && <LoadingState label="Loading cases…" />}
      {loaded?.error && <Alert tone="error">{loaded.error}</Alert>}

      {pool && (
        <AlibiAccessRequests
          eventId={event.id}
          canApprove={canApprove}
          refreshKey={tick}
          onDecided={refresh}
        />
      )}

      {pool && (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle>Pool ({cases.length})</CardTitle>
              <div className="flex items-center gap-2">
                <Badge tone={readyCount > 0 ? "success" : "warning"}>
                  {readyCount} ready
                </Badge>
                <Button variant="secondary" onClick={refresh}>
                  Refresh
                </Button>
              </div>
            </div>
          </CardHeader>

          {cases.length === 0 ? (
            <EmptyState
              icon="🕵️"
              title="No cases yet"
              description="Create the first case above."
            />
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
                      <Badge>
                        {c.questionCount}/{ALIBI_QUESTION_COUNT} questions
                      </Badge>
                      <Badge>{c.fixedFactCount} facts</Badge>
                      {c.currentVersion > 0 && <Badge>v{c.currentVersion}</Badge>}
                      {c.seenByMe && <Badge tone="accent">You know it</Badge>}
                    </div>
                    <p className="text-xs text-muted">
                      Written by {c.writtenBy} · last edit by {c.lastEditedBy}
                    </p>
                  </div>
                  {c.canOpen ? (
                    <Button variant="secondary" onClick={() => requestOpen(c)}>
                      Open
                    </Button>
                  ) : c.myRequestStatus === "pending" ? (
                    <Badge tone="warning">Request sent</Badge>
                  ) : (
                    <div className="flex items-center gap-2">
                      {c.myRequestStatus === "refused" && (
                        <Badge tone="danger">Refused</Badge>
                      )}
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setAskError(null);
                          setAsking(c);
                        }}
                      >
                        🔒 Ask to open
                      </Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      <Dialog
        open={confirmOpen !== null}
        onOpenChange={(next) => {
          if (!next) setConfirmOpen(null);
        }}
        title={`Open “${confirmOpen?.label ?? ""}”?`}
        description="You will see its questions and answers. Your team will then never be given this case."
      >
        <div className="mt-4 flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirmOpen(null)}>
            Cancel
          </Button>
          <Button
            onClick={() => {
              if (confirmOpen) setOpenCaseId(confirmOpen.id);
              setConfirmOpen(null);
            }}
          >
            Open anyway
          </Button>
        </div>
      </Dialog>

      <Dialog
        open={asking !== null}
        onOpenChange={(next) => {
          if (!next && !sending) setAsking(null);
        }}
        busy={sending}
        title={`Ask to open “${asking?.label ?? ""}”`}
        description="The event creator will approve or refuse. If approved, your team will never be given this case."
      >
        <div className="mt-4 space-y-4">
          <Textarea
            label="Why do you need to open it?"
            hint="For example: “I'm checking the questions for typos.”"
            rows={3}
            maxLength={ALIBI_REASON_MAX}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          {askError && <Alert tone="error">{askError}</Alert>}
          <div className="flex flex-wrap justify-end gap-2">
            <Button
              variant="secondary"
              disabled={sending}
              onClick={() => setAsking(null)}
            >
              Cancel
            </Button>
            <Button
              loading={sending}
              loadingText="Sending…"
              disabled={!reason.trim()}
              onClick={() => void sendRequest()}
            >
              Send request
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
