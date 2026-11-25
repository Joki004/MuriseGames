"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Button, Dialog } from "@/component/ui";
import {
  clearChatMatch,
  creatorFor,
  expireGuess,
  isFinished,
  isRunning,
  loadChatMatch,
  markGuess,
  newChatMatch,
  nextRound,
  pauseClock,
  remainingMs,
  rotate,
  roundValues,
  startClock,
  startGuessing,
  storeChatMatch,
  totalPoints,
  undo,
  type ChatMatchState,
} from "@/modules/Event/GroupChat";
import {
  PlayerPointsController,
  type FixturePlayer,
} from "@/modules/Event/PlayerPoints";
import { ScoringController } from "@/modules/Event/Scoring";

interface GroupChatTrackerDialogProps {
  eventId: string;
  fixtureId: string;
  title: string;
  team: { teamId: string; teamName: string };
  roundCount: number;
  onClose: () => void;
  onSaved?: () => void;
}

function formatSeconds(ms: number) {
  const s = Math.ceil(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

/**
 * Group Chat Meltdown, live (GM-050): the judge sees who creates this round,
 * runs the 60 s choosing clock and the 45 s guessing clock, and taps Guessed or
 * Not guessed. Time up scores 0. At the end it records the official result.
 * Situations stay on paper with the host; nothing secret is shown here.
 * Kept on this device until saved, so a refresh loses nothing.
 */
export function GroupChatTrackerDialog({
  eventId,
  fixtureId,
  title,
  team,
  roundCount,
  onClose,
  onSaved,
}: GroupChatTrackerDialogProps) {
  const [players, setPlayers] = useState<FixturePlayer[]>([]);
  const [match, setMatch] = useState<ChatMatchState | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let cancelled = false;

    function start(loaded: FixturePlayer[]) {
      if (cancelled) return;
      setPlayers(loaded);
      setMatch(loadChatMatch(fixtureId) ?? newChatMatch(fixtureId, roundCount));
    }

    PlayerPointsController.fixture(eventId, fixtureId)
      .then((data) => start(data.teams.find((t) => t.teamId === team.teamId)?.players ?? []))
      .catch(() => start([]));

    return () => {
      cancelled = true;
    };
  }, [eventId, fixtureId, team.teamId, roundCount]);

  const round = match ? match.rounds[match.current] : null;
  const running = round ? isRunning(round) : false;

  // Clock: ticks while running; when guessing time is up the round scores 0.
  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => {
      const tick = Date.now();
      setNow(tick);
      setMatch((current) => {
        if (!current) return current;
        const r = current.rounds[current.current];
        if (!r || r.endsAt === null || remainingMs(r, tick) > 0) return current;
        navigator.vibrate?.(400);
        // Choosing time up: just stop the clock (the host decides); guessing: 0 point.
        const next = r.phase === "guess" ? expireGuess(current) : pauseClock(current, tick);
        storeChatMatch(next);
        return next;
      });
    }, 250);
    return () => clearInterval(id);
  }, [running]);

  function update(change: (current: ChatMatchState, at: number) => ChatMatchState) {
    if (!match) return;
    const at = Date.now();
    const next = change(match, at);
    storeChatMatch(next);
    setNow(at);
    setMatch(next);
  }

  async function saveResult() {
    if (!match || !isFinished(match)) return;
    setBusy(true);
    setError(null);
    try {
      await ScoringController.recordFixtureResult(eventId, fixtureId, {
        teamValues: { [team.teamId]: roundValues(match) },
        reason: `Round tracker: ${totalPoints(match)} / ${roundCount} guessed`,
      });
      clearChatMatch(fixtureId);
      setSaved(true);
      onSaved?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the result.");
    } finally {
      setBusy(false);
    }
  }

  const finished = match ? isFinished(match) : false;
  const left = round ? remainingMs(round, now) : 0;
  const creator = match ? creatorFor(players, match.current, match.rotation) : null;

  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next && !busy) onClose();
      }}
      busy={busy}
      title={title}
      description={`Group Chat Meltdown · ${team.teamName}`}
    >
      <div className="mt-4 space-y-5">
        {error && <Alert tone="error">{error}</Alert>}

        {!match ? (
          <p className="text-sm text-muted">Loading players…</p>
        ) : saved ? (
          <div className="space-y-3">
            <Alert tone="success">
              Result saved: {totalPoints(match)} / {roundCount} situations guessed.
            </Alert>
            <Button onClick={onClose}>Done</Button>
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between text-lg font-bold">
              <span className="truncate">{team.teamName}</span>
              <span className="font-mono">
                {totalPoints(match)} / {roundCount}
              </span>
            </div>

            <ol className="grid grid-cols-4 gap-2 text-center text-xs">
              {match.rounds.map((r, i) => (
                <li
                  key={i}
                  className={[
                    "rounded-[var(--ui-radius-control)] border px-1 py-1.5",
                    i === match.current && !finished ? "border-accent" : "border-border",
                  ].join(" ")}
                >
                  <p className="font-semibold">Round {i + 1}</p>
                  <p className="text-muted">
                    {r.point === 1 ? "✓" : r.point === 0 ? (r.timedOut ? "⏱" : "✗") : "—"}
                  </p>
                </li>
              ))}
            </ol>

            {!finished && round && (
              <div className="space-y-4 rounded-[var(--ui-radius-card)] border border-border p-4">
                <p className="text-center text-sm font-semibold uppercase tracking-wider text-muted">
                  Round {match.current + 1} of {match.rounds.length} ·{" "}
                  {round.phase === "select"
                    ? "choosing images"
                    : round.phase === "guess"
                      ? "guessing"
                      : "done"}
                </p>

                {players.length > 0 && (
                  <div className="flex flex-wrap items-center justify-center gap-2 text-sm">
                    <span className="text-muted">Creator:</span>
                    <span className="font-semibold">{creator?.displayName ?? "—"}</span>
                    {!round.started && round.phase === "select" && (
                      <Button variant="secondary" onClick={() => update((m) => rotate(m))}>
                        ↻ Other creator
                      </Button>
                    )}
                  </div>
                )}

                {round.phase !== "done" && (
                  <p
                    className={[
                      "text-center font-mono text-5xl font-extrabold tabular-nums",
                      left === 0 ? "text-danger" : "",
                    ].join(" ")}
                  >
                    {formatSeconds(left)}
                  </p>
                )}

                {round.phase === "select" && (
                  <div className="space-y-2">
                    <p className="text-center text-xs text-muted">
                      Hand the creator a secret situation. They pick 2–4 memes/GIFs on their
                      own phone.
                    </p>
                    {running ? (
                      <Button
                        variant="secondary"
                        fullWidth
                        onClick={() => update((m, at) => pauseClock(m, at))}
                      >
                        Pause
                      </Button>
                    ) : (
                      <Button
                        variant="secondary"
                        fullWidth
                        onClick={() => update((m, at) => startClock(m, at))}
                      >
                        {round.started ? "Resume choosing" : "Start 60 s to choose"}
                      </Button>
                    )}
                    <Button
                      fullWidth
                      className="py-5 text-base"
                      onClick={() => update((m, at) => startGuessing(m, at))}
                    >
                      Images ready · start 45 s guessing
                    </Button>
                  </div>
                )}

                {round.phase === "guess" && (
                  <div className="space-y-2">
                    {!running && (
                      <Button
                        variant="secondary"
                        fullWidth
                        onClick={() => update((m, at) => startClock(m, at))}
                      >
                        Resume guessing
                      </Button>
                    )}
                    <div className="grid grid-cols-2 gap-2">
                      <Button
                        className="py-5 text-base"
                        onClick={() => update((m, at) => markGuess(m, true, at))}
                      >
                        ✓ Guessed
                      </Button>
                      <Button
                        variant="secondary"
                        className="py-5 text-base"
                        onClick={() => update((m, at) => markGuess(m, false, at))}
                      >
                        ✗ Not guessed
                      </Button>
                    </div>
                  </div>
                )}

                {round.phase === "done" ? (
                  <div className="space-y-3 text-center">
                    <p className="text-lg font-bold">
                      Round {match.current + 1}:{" "}
                      {round.point === 1
                        ? "guessed! +1"
                        : round.timedOut
                          ? "time's up, 0"
                          : "not guessed, 0"}
                    </p>
                    <div className="flex justify-center gap-2">
                      <Button variant="secondary" onClick={() => update((m) => undo(m))}>
                        Undo
                      </Button>
                      <Button onClick={() => update((m) => nextRound(m))}>Next round</Button>
                    </div>
                  </div>
                ) : (
                  <div className="flex justify-center">
                    <Button
                      variant="secondary"
                      disabled={!round.started && match.current === 0}
                      onClick={() => update((m) => undo(m))}
                    >
                      Undo
                    </Button>
                  </div>
                )}
              </div>
            )}

            {finished && (
              <div className="space-y-3 text-center">
                <Badge tone="success">All {match.rounds.length} rounds played</Badge>
                <p className="text-sm text-muted">
                  Saving records the official result: {totalPoints(match)} / {roundCount}{" "}
                  guessed.
                </p>
                <div className="flex justify-center gap-2">
                  <Button variant="secondary" disabled={busy} onClick={() => update((m) => undo(m))}>
                    Undo
                  </Button>
                  <Button loading={busy} onClick={() => void saveResult()}>
                    Save result
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </Dialog>
  );
}
