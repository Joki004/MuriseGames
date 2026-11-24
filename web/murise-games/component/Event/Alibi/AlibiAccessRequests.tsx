"use client";

import { useEffect, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
} from "@/component/ui";
import {
  AlibiController,
  type AlibiAccessRequest,
  type AlibiRequestStatus,
} from "@/modules/Event/Alibi";

const STATUS: Record<AlibiRequestStatus, { label: string; tone: "warning" | "success" | "danger" }> = {
  pending: { label: "Waiting", tone: "warning" },
  approved: { label: "Approved", tone: "success" },
  refused: { label: "Refused", tone: "danger" },
};

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

interface Loaded {
  tick: number;
  requests: AlibiAccessRequest[] | null;
  error: string | null;
}

/**
 * The event creator answers requests here; everyone else sees their own requests.
 * Renders nothing when there is nothing to show.
 */
export function AlibiAccessRequests({
  eventId,
  canApprove,
  refreshKey,
  onDecided,
}: {
  eventId: string;
  canApprove: boolean;
  refreshKey: number;
  onDecided: () => void;
}) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);

  useEffect(() => {
    let cancelled = false;
    AlibiController.listRequests(eventId)
      .then((requests) => {
        if (!cancelled) setLoaded({ tick: refreshKey, requests, error: null });
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setLoaded({
            tick: refreshKey,
            requests: null,
            error:
              err instanceof Error ? err.message : "Could not load the access requests.",
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, refreshKey]);

  if (!loaded) return null;
  if (loaded.error) return <Alert tone="error">{loaded.error}</Alert>;

  const requests = loaded.requests ?? [];
  if (requests.length === 0) return null;

  const pending = requests.filter((r) => r.status === "pending");
  const decided = requests.filter((r) => r.status !== "pending").slice(0, 10);

  return (
    <Card className="space-y-4">
      <CardHeader>
        <div className="flex flex-wrap items-center gap-2">
          <CardTitle>{canApprove ? "Access requests" : "Your access requests"}</CardTitle>
          {pending.length > 0 && <Badge tone="warning">{pending.length} waiting</Badge>}
        </div>
        <CardDescription>
          {canApprove
            ? "People asking to open a case they did not write. Approving means their team will never be given that case."
            : "The event creator approves or refuses each request."}
        </CardDescription>
      </CardHeader>

      {pending.length > 0 && (
        <ul className="space-y-3">
          {pending.map((request) =>
            canApprove ? (
              <PendingDecision
                key={request.id}
                eventId={eventId}
                request={request}
                onDecided={onDecided}
              />
            ) : (
              <RequestRow key={request.id} request={request} />
            ),
          )}
        </ul>
      )}

      {decided.length > 0 && (
        <div className="space-y-2">
          <p className="text-sm font-semibold">Answered</p>
          <ul className="space-y-2">
            {decided.map((request) => (
              <RequestRow key={request.id} request={request} />
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

function RequestRow({ request }: { request: AlibiAccessRequest }) {
  const status = STATUS[request.status];
  return (
    <li className="space-y-1 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-medium">
          {request.mine ? "You" : request.requester} → {request.caseLabel}
        </p>
        <Badge tone={status.tone}>{status.label}</Badge>
      </div>
      <p className="text-muted">“{request.reason}”</p>
      {request.decidedBy && request.decidedAt && (
        <p className="text-xs text-muted">
          {status.label} by {request.decidedBy} · {formatWhen(request.decidedAt)}
          {request.decisionNote ? ` — ${request.decisionNote}` : ""}
        </p>
      )}
    </li>
  );
}

function PendingDecision({
  eventId,
  request,
  onDecided,
}: {
  eventId: string;
  request: AlibiAccessRequest;
  onDecided: () => void;
}) {
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState<"approve" | "refuse" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function decide(approve: boolean) {
    setBusy(approve ? "approve" : "refuse");
    setError(null);
    try {
      await AlibiController.decide(eventId, request.id, approve, note);
      onDecided();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not answer the request.");
      setBusy(null);
    }
  }

  return (
    <li className="space-y-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3">
      <div className="space-y-1 text-sm">
        <p className="font-medium">
          {request.requester} wants to open “{request.caseLabel}”
        </p>
        <p className="text-muted">“{request.reason}”</p>
        <p className="text-xs text-muted">Asked {formatWhen(request.createdAt)}</p>
      </div>
      <Input
        label="Note for them (optional)"
        maxLength={500}
        value={note}
        onChange={(e) => setNote(e.target.value)}
      />
      <div className="flex flex-wrap gap-2">
        <Button
          loading={busy === "approve"}
          loadingText="Approving…"
          disabled={busy !== null}
          onClick={() => void decide(true)}
        >
          Approve
        </Button>
        <Button
          variant="danger"
          loading={busy === "refuse"}
          loadingText="Refusing…"
          disabled={busy !== null}
          onClick={() => void decide(false)}
        >
          Refuse
        </Button>
      </div>
      {error && <Alert tone="error">{error}</Alert>}
    </li>
  );
}
