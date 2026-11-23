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
  ClueController,
  type ReleasedCluesResponse,
} from "@/modules/Event/Clue";
import {
  GuessController,
  type CaptainGuessBoardResponse,
  type SubmitGuessRequest,
} from "@/modules/Event/Guess";

interface SecretSceneGuessPanelProps {
  eventId: string;
  snapshotVersion?: number;
}

export function SecretSceneGuessPanel({
  eventId,
  snapshotVersion,
}: SecretSceneGuessPanelProps) {
  const [board, setBoard] = useState<CaptainGuessBoardResponse | null>(null);
  const [released, setReleased] = useState<ReleasedCluesResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyTeamId, setBusyTeamId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [pending, setPending] = useState<Record<string, SubmitGuessRequest>>(
    {},
  );

  useEffect(() => {
    let active = true;

    void Promise.all([
      GuessController.getBoard(eventId),
      ClueController.getReleased(eventId),
    ])
      .then(([b, r]) => {
        if (!active) return;
        setBoard(b);
        setReleased(r);
        setError(null);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setError(
          err instanceof Error
            ? err.message
            : "Could not load secret scene clues.",
        );
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [eventId, snapshotVersion]);

  async function refreshState() {
    const [b, r] = await Promise.all([
      GuessController.getBoard(eventId),
      ClueController.getReleased(eventId),
    ]);
    setBoard(b);
    setReleased(r);
  }

  async function handleSubmit(targetTeamId: string) {
    if (!board || !board.captain || board.guessingStatus !== "open") return;
    const text = (drafts[targetTeamId] ?? "").trim();
    if (!text) return;

    setBusyTeamId(targetTeamId);
    setError(null);
    setNotice(null);

    const existing = pending[targetTeamId];
    const command: SubmitGuessRequest =
      existing &&
      existing.expectedStage === board.currentStage &&
      existing.expectedVersion === board.version
        ? { ...existing, guess: text }
        : {
            requestId: crypto.randomUUID(),
            targetTeamId,
            expectedStage: board.currentStage,
            expectedVersion: board.version,
            guess: text,
          };

    setPending((prev) => ({ ...prev, [targetTeamId]: command }));

    try {
      const receipt = await GuessController.submit(eventId, command);
      await refreshState();
      setDrafts((prev) => ({ ...prev, [targetTeamId]: "" }));
      setPending((prev) => {
        const next = { ...prev };
        delete next[targetTeamId];
        return next;
      });
      setNotice(
        `Receipt confirmed for ${receipt.targetTeamName} (Stage ${receipt.stage}). Verdict stays secret until the finale.`,
      );
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not submit guess.",
      );
      await refreshState().catch(() => undefined);
    } finally {
      setBusyTeamId(null);
    }
  }

  if (loading) {
    return (
      <Card>
        <p className="text-sm text-muted">Loading Secret Scene clues…</p>
      </Card>
    );
  }

  if (!board || !board.guessingTeamId) {
    return null;
  }

  const isClosed = board.guessingStatus === "closed";
  const currentStage = board.currentStage;
  const clues = released?.clues ?? [];

  return (
    <Card className="space-y-5">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <CardTitle>Secret Scene Premiere · Clues &amp; Guesses</CardTitle>
            <CardDescription>
              Stage {currentStage} of 3 · One guess per opposing team per stage.
              Verdicts remain secret until the finale.
            </CardDescription>
          </div>
          <Badge tone={isClosed ? "warning" : "success"}>
            {isClosed ? "Guessing closed" : `Stage ${currentStage} open`}
          </Badge>
        </div>
      </CardHeader>

      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      {!board.captain && (
        <Alert tone="info">
          Only your team captain can submit official guesses. You can still view
          released clues and your team&apos;s receipts below.
        </Alert>
      )}

      {board.targets.length === 0 ? (
        <p className="text-sm text-muted">No opposing teams in this event.</p>
      ) : (
        <div className="space-y-4">
          {board.targets.map((target) => {
            const targetClues = clues.filter(
              (c) => c.teamId === target.teamId,
            );
            const targetReceipts = board.submittedGuesses.filter(
              (g) => g.targetTeamId === target.teamId,
            );
            const alreadyGuessedThisStage = targetReceipts.some(
              (g) => g.stage === currentStage,
            );

            return (
              <div
                key={target.teamId}
                className="space-y-3 rounded-[var(--ui-radius-control)] border border-border p-4"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span
                      className="h-3 w-3 rounded-full"
                      style={{ backgroundColor: target.teamColor }}
                    />
                    <span className="font-semibold">{target.teamName}</span>
                  </div>
                  {alreadyGuessedThisStage && (
                    <Badge tone="success">
                      Stage {currentStage} received
                    </Badge>
                  )}
                </div>

                {targetClues.length === 0 ? (
                  <p className="text-xs text-muted">
                    No clues released yet (Stage 0).
                  </p>
                ) : (
                  <ul className="space-y-1 text-sm">
                    {targetClues.map((c) => (
                      <li key={c.stage}>
                        <span className="font-medium text-muted">
                          Clue {c.stage}:
                        </span>{" "}
                        {c.body}
                      </li>
                    ))}
                  </ul>
                )}

                {targetReceipts.length > 0 && (
                  <div className="space-y-1 border-t border-border/60 pt-2 text-xs text-muted">
                    <p className="font-medium">Neutral receipts:</p>
                    {targetReceipts.map((r) => (
                      <p key={r.id}>
                        Stage {r.stage} · Received · &ldquo;{r.rawGuess}&rdquo;
                      </p>
                    ))}
                  </div>
                )}

                {board.captain &&
                  !isClosed &&
                  target.hasRegisteredScene &&
                  !alreadyGuessedThisStage && (
                    <div className="flex flex-col gap-2 pt-1 sm:flex-row">
                      <div className="flex-1">
                        <Input
                          label={`Stage ${currentStage} guess for ${target.teamName}`}
                          placeholder="Film, show, or stage play title"
                          maxLength={160}
                          value={drafts[target.teamId] ?? ""}
                          onChange={(e) =>
                            setDrafts((prev) => ({
                              ...prev,
                              [target.teamId]: e.target.value,
                            }))
                          }
                        />
                      </div>
                      <div className="self-end">
                        <Button
                          loading={busyTeamId === target.teamId}
                          disabled={
                            busyTeamId !== null ||
                            !(drafts[target.teamId] ?? "").trim()
                          }
                          onClick={() => void handleSubmit(target.teamId)}
                        >
                          Submit guess
                        </Button>
                      </div>
                    </div>
                  )}
              </div>
            );
          })}
        </div>
      )}
    </Card>
  );
}