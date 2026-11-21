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
import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { EventLiveController, useEventSnapshot } from "@/modules/Event/Live";

interface EventLivePanelProps {
  eventId: string;
  canManage: boolean;
}

function formatMs(ms: number) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

export function EventLivePanel({ eventId, canManage }: EventLivePanelProps) {
  const { snapshot, clockOffsetMs, loading, error, applySnapshot, refetch } =
    useEventSnapshot(eventId);

  const [nowMs, setNowMs] = useState(() => Date.now());
  const [phaseInput, setPhaseInput] = useState("active_round");
  const [secondsInput, setSecondsInput] = useState("60");
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (!snapshot?.deadlineAt || snapshot.paused) return;
    const id = setInterval(() => setNowMs(Date.now()), 250);
    return () => clearInterval(id);
  }, [snapshot?.deadlineAt, snapshot?.paused]);

  const remainingMs = (() => {
    if (!snapshot) return null;
    if (snapshot.paused && snapshot.pausedRemainingMs != null) {
      return snapshot.pausedRemainingMs;
    }
    if (snapshot.deadlineAt) {
      const deadline = Date.parse(snapshot.deadlineAt);
      const serverNow = nowMs + clockOffsetMs;
      return Math.max(0, deadline - serverNow);
    }
    return null;
  })();

  async function runCommand(action: () => Promise<void>) {
    setBusy(true);
    setActionError(null);
    try {
      await action();
    } catch (err: unknown) {
      setActionError(extractApiErrorMessage(err, "Command failed."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <CardTitle>Live event status</CardTitle>
            <CardDescription>
              Synced automatically from the server every 2 seconds.
            </CardDescription>
          </div>

          <div className="flex items-center gap-2">
            {snapshot && (
              <>
                <Badge tone={snapshot.paused ? "warning" : "success"}>
                  {snapshot.paused ? "Paused" : snapshot.phase}
                </Badge>
                <Badge>v{snapshot.version}</Badge>
              </>
            )}
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => void refetch()}
            >
              Sync now
            </Button>
          </div>
        </div>
      </CardHeader>

      {error && (
        <div className="mb-4">
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      {actionError && (
        <div className="mb-4">
          <Alert tone="error">{actionError}</Alert>
        </div>
      )}

      {loading && !snapshot ? (
        <p className="text-sm text-muted">Syncing live state…</p>
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-baseline justify-between gap-4 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-4">
            <div>
              <p className="text-xs uppercase tracking-wider text-muted">
                Current phase
              </p>
              <p className="mt-1 text-lg font-semibold">
                {snapshot?.phase ?? "lobby"}
              </p>
            </div>

            <div className="text-right">
              <p className="text-xs uppercase tracking-wider text-muted">
                Timer
              </p>
              <p className="mt-1 font-mono text-2xl font-bold">
                {remainingMs != null ? formatMs(remainingMs) : "--:--"}
              </p>
            </div>
          </div>

          {canManage && (
            <div className="grid gap-3 border-t border-border pt-4 sm:grid-cols-[1fr_8rem_auto]">
              <Input
                label="Phase"
                value={phaseInput}
                onChange={(e) => setPhaseInput(e.target.value)}
                placeholder="e.g. active_round"
              />
              <Input
                label="Seconds"
                type="number"
                min={0}
                value={secondsInput}
                onChange={(e) => setSecondsInput(e.target.value)}
              />
              <div className="flex flex-wrap items-end gap-2">
                <Button
                  disabled={busy || !phaseInput.trim()}
                  onClick={() =>
                    void runCommand(async () => {
                      const duration = Number.parseInt(secondsInput, 10);
                      const updated = await EventLiveController.updateState(
                        eventId,
                        {
                          currentEventGameId:
                            snapshot?.currentEventGameId ?? null,
                          phase: phaseInput.trim(),
                          durationSeconds: Number.isNaN(duration)
                            ? null
                            : duration,
                        }
                      );
                      applySnapshot(updated);
                    })
                  }
                >
                  Start / Update
                </Button>

                {snapshot?.deadlineAt && !snapshot.paused && (
                  <Button
                    variant="secondary"
                    disabled={busy}
                    onClick={() =>
                      void runCommand(async () => {
                        const updated =
                          await EventLiveController.pause(eventId);
                        applySnapshot(updated);
                      })
                    }
                  >
                    Pause
                  </Button>
                )}

                {snapshot?.paused && (
                  <Button
                    variant="secondary"
                    disabled={busy}
                    onClick={() =>
                      void runCommand(async () => {
                        const updated =
                          await EventLiveController.resume(eventId);
                        applySnapshot(updated);
                      })
                    }
                  >
                    Resume
                  </Button>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}