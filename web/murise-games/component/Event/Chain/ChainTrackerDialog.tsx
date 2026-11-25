"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Button, Dialog } from "@/component/ui";
import {
  ROLES,
  clearChainMatch,
  expireTurn,
  isFinished,
  isTurnOver,
  loadChainMatch,
  newChainMatch,
  nextTurn,
  passUsed,
  pauseTimer,
  recordCard,
  remainingMs,
  rolesFor,
  rotate,
  startTimer,
  storeChainMatch,
  totalScore,
  turnScore,
  turnValues,
  undoCard,
  type CardOutcome,
  type ChainMatchState,
} from "@/modules/Event/Chain";
import {
  PlayerPointsController,
  type FixturePlayer,
} from "@/modules/Event/PlayerPoints";
import { ScoringController } from "@/modules/Event/Scoring";

interface ChainTrackerDialogProps {
  eventId: string;
  fixtureId: string;
  title: string;
  team: { teamId: string; teamName: string };
  turnCount: number;
  cardsPerTurn: number;
  onClose: () => void;
  onSaved?: () => void;
}

const OUTCOME_LABEL: Record<CardOutcome, string> = {
  correct: "✓",
  pass: "⏭",
  invalid: "✗",
  time: "⏱",
};

function formatSeconds(ms: number) {
  const s = Math.ceil(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

/**
 * The Sensory Chain, live (GM-045): the judge runs the 45 s clock, sees who
 * plays which role, and taps each card (correct, pass, voided). Cards left when
 * time runs out score 0. At the end it records the official result.
 * The action cards stay printed; nothing secret is shown here.
 * Kept on this device until saved, so a refresh loses nothing.
 */
export function ChainTrackerDialog({
  eventId,
  fixtureId,
  title,
  team,
  turnCount,
  cardsPerTurn,
  onClose,
  onSaved,
}: ChainTrackerDialogProps) {
  const [players, setPlayers] = useState<FixturePlayer[]>([]);
  const [match, setMatch] = useState<ChainMatchState | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let cancelled = false;

    function start(loaded: FixturePlayer[]) {
      if (cancelled) return;
      setPlayers(loaded);
      setMatch(loadChainMatch(fixtureId) ?? newChainMatch(fixtureId, turnCount, cardsPerTurn));
    }

    PlayerPointsController.fixture(eventId, fixtureId)
      .then((data) => start(data.teams.find((t) => t.teamId === team.teamId)?.players ?? []))
      .catch(() => start([]));

    return () => {
      cancelled = true;
    };
  }, [eventId, fixtureId, team.teamId, turnCount, cardsPerTurn]);

  const running = match?.turns[match.current]?.endsAt != null;

  // Clock: ticks while running; at 0 the turn ends and unplayed cards score 0.
  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => {
      const tick = Date.now();
      setNow(tick);
      setMatch((current) => {
        if (!current) return current;
        const turn = current.turns[current.current];
        if (!turn || turn.endsAt === null || remainingMs(turn, tick) > 0) return current;
        const next = expireTurn(current);
        storeChainMatch(next);
        navigator.vibrate?.(400);
        return next;
      });
    }, 250);
    return () => clearInterval(id);
  }, [running]);

  function update(change: (current: ChainMatchState, at: number) => ChainMatchState) {
    if (!match) return;
    const at = Date.now();
    const next = change(match, at);
    storeChainMatch(next);
    setNow(at);
    setMatch(next);
  }

  async function saveResult() {
    if (!match || !isFinished(match)) return;
    setBusy(true);
    setError(null);
    try {
      await ScoringController.recordFixtureResult(eventId, fixtureId, {
        teamValues: { [team.teamId]: turnValues(match) },
        reason: `Turn tracker: ${totalScore(match)} / ${turnCount * cardsPerTurn} cards`,
      });
      clearChainMatch(fixtureId);
      setSaved(true);
      onSaved?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the result.");
    } finally {
      setBusy(false);
    }
  }

  const turn = match ? match.turns[match.current] : null;
  const over = match ? isTurnOver(match, match.current) : false;
  const finished = match ? isFinished(match) : false;
  const left = turn ? remainingMs(turn, now) : 0;
  const roles = match ? rolesFor(players, match.current, match.rotation) : [];
  const maxRaw = turnCount * cardsPerTurn;

  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next && !busy) onClose();
      }}
      busy={busy}
      title={title}
      description={`The Sensory Chain · ${team.teamName}`}
    >
      <div className="mt-4 space-y-5">
        {error && <Alert tone="error">{error}</Alert>}

        {!match ? (
          <p className="text-sm text-muted">Loading players…</p>
        ) : saved ? (
          <div className="space-y-3">
            <Alert tone="success">
              Result saved: {totalScore(match)} / {maxRaw} correct cards.
            </Alert>
            <Button onClick={onClose}>Done</Button>
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between text-lg font-bold">
              <span className="truncate">{team.teamName}</span>
              <span className="font-mono">
                {totalScore(match)} / {maxRaw}
              </span>
            </div>

            <ol className="grid grid-cols-4 gap-2 text-center text-xs">
              {match.turns.map((t, i) => (
                <li
                  key={i}
                  className={[
                    "rounded-[var(--ui-radius-control)] border px-1 py-1.5",
                    i === match.current && !finished ? "border-accent" : "border-border",
                  ].join(" ")}
                >
                  <p className="font-semibold">Turn {i + 1}</p>
                  <p className="font-mono text-muted">
                    {t.outcomes.length > 0 ? t.outcomes.map((o) => OUTCOME_LABEL[o]).join(" ") : "—"}
                  </p>
                </li>
              ))}
            </ol>

            {!finished && turn && (
              <div className="space-y-4 rounded-[var(--ui-radius-card)] border border-border p-4">
                <p className="text-center text-sm font-semibold uppercase tracking-wider text-muted">
                  Turn {match.current + 1} of {match.turns.length}
                </p>

                {players.length > 0 && (
                  <div className="space-y-1">
                    <ul className="grid grid-cols-2 gap-1 text-sm">
                      {ROLES.map((role, i) => (
                        <li key={role} className="truncate">
                          <span className="text-muted">{role}:</span>{" "}
                          <span className="font-semibold">
                            {roles[i]?.displayName ?? "host"}
                          </span>
                        </li>
                      ))}
                    </ul>
                    {!turn.started && (
                      <Button variant="secondary" onClick={() => update((m) => rotate(m))}>
                        ↻ Shift roles
                      </Button>
                    )}
                  </div>
                )}

                <p
                  className={[
                    "text-center font-mono text-5xl font-extrabold tabular-nums",
                    left === 0 ? "text-danger" : "",
                  ].join(" ")}
                >
                  {formatSeconds(left)}
                </p>

                {over ? (
                  <div className="space-y-3 text-center">
                    <p className="text-lg font-bold">
                      Turn {match.current + 1}: {turnScore(turn)} / {cardsPerTurn} correct
                    </p>
                    <div className="flex justify-center gap-2">
                      <Button variant="secondary" onClick={() => update((m) => undoCard(m))}>
                        Undo
                      </Button>
                      <Button onClick={() => update((m) => nextTurn(m))}>Next turn</Button>
                    </div>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {running ? (
                      <Button
                        variant="secondary"
                        fullWidth
                        onClick={() => update((m, at) => pauseTimer(m, at))}
                      >
                        Pause
                      </Button>
                    ) : (
                      <Button fullWidth onClick={() => update((m, at) => startTimer(m, at))}>
                        {turn.started ? "Resume" : "Start 45 s"}
                      </Button>
                    )}

                    <p className="text-center text-xs text-muted">
                      Card {turn.outcomes.length + 1} of {cardsPerTurn}
                    </p>
                    <Button
                      fullWidth
                      className="py-5 text-base"
                      disabled={!turn.started}
                      onClick={() => update((m, at) => recordCard(m, "correct", at))}
                    >
                      ✓ Correct
                    </Button>
                    <div className="grid grid-cols-2 gap-2">
                      <Button
                        variant="secondary"
                        disabled={!turn.started || passUsed(turn)}
                        onClick={() => update((m, at) => recordCard(m, "pass", at))}
                      >
                        ⏭ Pass{passUsed(turn) ? " (used)" : ""}
                      </Button>
                      <Button
                        variant="secondary"
                        disabled={!turn.started}
                        onClick={() => update((m, at) => recordCard(m, "invalid", at))}
                      >
                        ✗ Voided
                      </Button>
                    </div>
                    <p className="text-center text-xs text-muted">
                      Voided = spoken clue, mouthing, peeking or help from the helper.
                    </p>
                    <div className="flex justify-center">
                      <Button
                        variant="secondary"
                        disabled={turn.outcomes.length === 0 && match.current === 0}
                        onClick={() => update((m) => undoCard(m))}
                      >
                        Undo
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {finished && (
              <div className="space-y-3 text-center">
                <Badge tone="success">All {match.turns.length} turns played</Badge>
                <p className="text-sm text-muted">
                  Saving records the official result: {totalScore(match)} / {maxRaw} correct
                  cards.
                </p>
                <div className="flex justify-center gap-2">
                  <Button variant="secondary" disabled={busy} onClick={() => update((m) => undoCard(m))}>
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
