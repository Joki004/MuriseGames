"use client";

import { useEffect, useState } from "react";
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
} from "@/component/ui";
import {
  ClueController,
  type ReleasedCluesResponse,
  type StageCommandRequest,
} from "@/modules/Event/Clue";

export function ClueReleasePanel() {
  const { event, canManage } = useEventDetail();

  const [state, setState] = useState<ReleasedCluesResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  const [pendingRelease, setPendingRelease] =
    useState<StageCommandRequest | null>(null);
  const [confirmMode, setConfirmMode] = useState<"release" | "close" | null>(
    null,
  );

  useEffect(() => {
    let active = true;

    void ClueController.getReleased(event.id)
      .then((data) => {
        if (active) {
          setState(data);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(
            err instanceof Error
              ? err.message
              : "Could not load clue release state.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [event.id, tick]);

  async function executeRelease() {
    if (!state) return;
    setBusy(true);
    setError(null);
    setNotice(null);

    const command: StageCommandRequest = pendingRelease ?? {
      requestId: crypto.randomUUID(),
      expectedStage: state.currentStage,
      expectedVersion: state.version,
    };
    setPendingRelease(command);

    try {
      const updated = await ClueController.releaseNext(event.id, command);
      setState(updated);
      setPendingRelease(null);
      setConfirmMode(null);
      setNotice(`Clue stage ${updated.currentStage} released to all screens.`);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not release clue stage.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function executeCloseGuessing() {
    if (!state) return;
    setBusy(true);
    setError(null);
    setNotice(null);

    try {
      const updated = await ClueController.closeGuessing(event.id, {
        requestId: crypto.randomUUID(),
        expectedStage: state.currentStage,
        expectedVersion: state.version,
      });
      setState(updated);
      setConfirmMode(null);
      setNotice("All guessing is now locked. Ready for title reveals.");
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not close guessing.",
      );
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <p className="text-sm text-muted">Loading clue release status…</p>;
  }

  if (!state) {
    return (
      <Card>
        <Alert tone="error">
          {error ?? "Could not load clue release status."}
        </Alert>
      </Card>
    );
  }

  const isClosed = state.status === "closed";
  const nextStage = state.currentStage + 1;
  const canReleaseMore = !isClosed && state.currentStage < 3;

  return (
    <Card className="space-y-4">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Clue release &amp; guessing stage</CardTitle>
            <CardDescription>
              Global stage {state.currentStage} of 3 · Version {state.version}
            </CardDescription>
          </div>
          <div className="flex items-center gap-2">
            <Badge tone={isClosed ? "warning" : "success"}>
              {isClosed ? "Guessing closed" : "Guessing open"}
            </Badge>
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => {
                setLoading(true);
                setPendingRelease(null);
                setTick((n) => n + 1);
              }}
            >
              Refresh
            </Button>
          </div>
        </div>
      </CardHeader>

      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      {canManage && (
        <div className="flex flex-wrap gap-3">
          {canReleaseMore && (
            <Button
              disabled={busy}
              onClick={() => setConfirmMode("release")}
            >
              {pendingRelease
                ? `Retry Stage ${nextStage} release`
                : `Release Clue Stage ${nextStage}`}
            </Button>
          )}
          {!isClosed && (
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => setConfirmMode("close")}
            >
              Close all guessing
            </Button>
          )}
        </div>
      )}

      {state.clues.length === 0 ? (
        <p className="text-sm text-muted">
          No clues have been released yet (Stage 0).
        </p>
      ) : (
        <div className="space-y-4">
          {[1, 2, 3]
            .filter((stage) => stage <= state.currentStage)
            .map((stage) => (
              <div key={stage} className="space-y-2">
                <h3 className="text-sm font-semibold">Stage {stage} clues</h3>
                <ul className="grid gap-2 sm:grid-cols-2">
                  {state.clues
                    .filter((c) => c.stage === stage)
                    .map((c) => (
                      <li
                        key={`${c.teamId}-${c.stage}`}
                        className="rounded-[var(--ui-radius-control)] border border-border p-3 text-sm"
                      >
                        <p className="font-medium">{c.teamName}</p>
                        <p className="mt-1 text-muted">{c.body}</p>
                      </li>
                    ))}
                </ul>
              </div>
            ))}
        </div>
      )}

      <Dialog
        open={confirmMode !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmMode(null);
        }}
        busy={busy}
        title={
          confirmMode === "close"
            ? "Close all guessing?"
            : `Release Clue Stage ${nextStage}?`
        }
        description={
          confirmMode === "close"
            ? "Teams will no longer be able to submit guesses. Do this before the first title reveal."
            : "This publishes Stage " +
              nextStage +
              " clues for every active team to all participant and TV screens. It cannot be undone."
        }
      >
        <div className="mt-4 flex flex-wrap gap-3">
          <Button
            loading={busy}
            onClick={() =>
              void (confirmMode === "close"
                ? executeCloseGuessing()
                : executeRelease())
            }
          >
            {confirmMode === "close"
              ? "Confirm close guessing"
              : `Confirm release Stage ${nextStage}`}
          </Button>
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => setConfirmMode(null)}
          >
            Cancel
          </Button>
        </div>
      </Dialog>
    </Card>
  );
}