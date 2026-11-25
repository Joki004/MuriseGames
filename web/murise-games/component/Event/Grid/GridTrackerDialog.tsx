"use client";

import { useEffect, useRef, useState } from "react";
import { Alert, Badge, Button, Dialog } from "@/component/ui";
import {
  RUNNERS,
  addCatch,
  clearGridMatch,
  cue,
  endTurn,
  hunterFor,
  isFinished,
  loadGridMatch,
  newGridMatch,
  nextTurn,
  replayCue,
  roundOf,
  runnersFor,
  storeGridMatch,
  teamValues,
  totals,
  undo,
  type GridMatchState,
  type Side,
} from "@/modules/Event/Grid";
import {
  PlayerPointsController,
  type FixturePlayer,
} from "@/modules/Event/PlayerPoints";
import { ScoringController } from "@/modules/Event/Scoring";

interface GridTrackerDialogProps {
  eventId: string;
  fixtureId: string;
  title: string;
  /** The two playing teams, in fixture order: A hunts first in each round. */
  teams: [{ teamId: string; teamName: string }, { teamId: string; teamName: string }];
  turnCount: number;
  onClose: () => void;
  onSaved?: () => void;
}

const CUE_INTERVALS = [2, 3, 4];

/** A short beep, the same for every cue. */
function playBeep(ctx: AudioContext | null) {
  if (!ctx) return;
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.frequency.value = 880;
  gain.gain.setValueAtTime(0.3, ctx.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15);
  osc.connect(gain).connect(ctx.destination);
  osc.start();
  osc.stop(ctx.currentTime + 0.15);
}

/**
 * Rhythm Grid Hunt, live (GM-049): the judge gives the 12 cues (by hand or with
 * the phone's beat), taps each catch, and the turn ends at 12 cues or 3
 * catches. Shows who hunts and who runs. At the end it records the official
 * result (catches / survivors per turn). Kept on this device until saved.
 */
export function GridTrackerDialog({
  eventId,
  fixtureId,
  title,
  teams,
  turnCount,
  onClose,
  onSaved,
}: GridTrackerDialogProps) {
  const [players, setPlayers] = useState<Record<Side, FixturePlayer[]>>({ A: [], B: [] });
  const [match, setMatch] = useState<GridMatchState | null>(null);
  const [autoCue, setAutoCue] = useState(false);
  const [cueSeconds, setCueSeconds] = useState(3);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const audioRef = useRef<AudioContext | null>(null);

  const [teamA, teamB] = teams;
  const nameOfTeam = (side: Side) => (side === "A" ? teamA.teamName : teamB.teamName);

  useEffect(() => {
    let cancelled = false;

    function start(loaded: Record<Side, FixturePlayer[]>) {
      if (cancelled) return;
      setPlayers(loaded);
      setMatch(loadGridMatch(fixtureId) ?? newGridMatch(fixtureId, turnCount));
    }

    PlayerPointsController.fixture(eventId, fixtureId)
      .then((data) => {
        const of = (teamId: string) => data.teams.find((t) => t.teamId === teamId)?.players ?? [];
        start({ A: of(teamA.teamId), B: of(teamB.teamId) });
      })
      .catch(() => start({ A: [], B: [] }));

    return () => {
      cancelled = true;
    };
  }, [eventId, fixtureId, turnCount, teamA.teamId, teamB.teamId]);

  const turn = match ? match.turns[match.current] : null;
  const beatRunning = autoCue && turn !== null && !turn.done;

  // Phone beat: one cue every few seconds until 12 cues or 3 catches.
  useEffect(() => {
    if (!beatRunning) return;
    const id = setInterval(() => {
      playBeep(audioRef.current);
      setMatch((current) => {
        if (!current) return current;
        const next = cue(current);
        storeGridMatch(next);
        return next;
      });
    }, cueSeconds * 1000);
    return () => clearInterval(id);
  }, [beatRunning, cueSeconds]);

  function update(change: (current: GridMatchState) => GridMatchState) {
    if (!match) return;
    const next = change(match);
    storeGridMatch(next);
    setMatch(next);
  }

  function startBeat() {
    if (!audioRef.current) {
      try {
        audioRef.current = new AudioContext();
      } catch {
        audioRef.current = null;
      }
    }
    void audioRef.current?.resume();
    setAutoCue(true);
  }

  async function saveResult() {
    if (!match || !isFinished(match)) return;
    setBusy(true);
    setError(null);
    try {
      const values = teamValues(match);
      const sum = totals(match);
      await ScoringController.recordFixtureResult(eventId, fixtureId, {
        teamValues: { [teamA.teamId]: values.A, [teamB.teamId]: values.B },
        reason: `Grid tracker: ${teamA.teamName} ${sum.A} – ${sum.B} ${teamB.teamName}`,
      });
      clearGridMatch(fixtureId);
      setSaved(true);
      onSaved?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the result.");
    } finally {
      setBusy(false);
    }
  }

  const finished = match ? isFinished(match) : false;
  const score = match ? totals(match) : { A: 0, B: 0 };
  const round = match ? roundOf(match.current) : 0;
  const hunterSide: Side = turn?.hunter ?? "A";
  const runnerSide: Side = hunterSide === "A" ? "B" : "A";
  const hunter = hunterFor(players[hunterSide], round);
  const runners = runnersFor(players[runnerSide], round);

  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next && !busy) {
          setAutoCue(false);
          onClose();
        }
      }}
      busy={busy}
      title={title}
      description="Rhythm Grid Hunt · one shared 3×3 grid, 12 cues per turn"
    >
      <div className="mt-4 space-y-5">
        {error && <Alert tone="error">{error}</Alert>}

        {!match || !turn ? (
          <p className="text-sm text-muted">Loading players…</p>
        ) : saved ? (
          <div className="space-y-3">
            <Alert tone="success">
              Result saved: {teamA.teamName} {score.A} – {score.B} {teamB.teamName}.
            </Alert>
            <Button onClick={onClose}>Done</Button>
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between gap-3 text-lg font-bold">
              <span className="truncate">{teamA.teamName}</span>
              <span className="font-mono">
                {score.A} – {score.B}
              </span>
              <span className="truncate text-right">{teamB.teamName}</span>
            </div>

            <ol className="grid grid-cols-4 gap-2 text-center text-xs">
              {Array.from({ length: match.turns.length / 2 }, (_, r) => (
                <li
                  key={r}
                  className={[
                    "rounded-[var(--ui-radius-control)] border px-1 py-1.5",
                    round === r && !finished ? "border-accent" : "border-border",
                  ].join(" ")}
                >
                  <p className="font-semibold">Round {r + 1}</p>
                  <p className="font-mono text-muted">
                    {[match.turns[r * 2], match.turns[r * 2 + 1]]
                      .map((t) => (t.done ? `${t.catches}🎯` : "—"))
                      .join(" ")}
                  </p>
                </li>
              ))}
            </ol>

            {!finished && (
              <div className="space-y-4 rounded-[var(--ui-radius-card)] border border-border p-4">
                <p className="text-center text-sm font-semibold uppercase tracking-wider text-muted">
                  Round {round + 1} · turn {(match.current % 2) + 1} of 2
                </p>

                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <p className="text-muted">Hunter · {nameOfTeam(hunterSide)}</p>
                    <p className="font-semibold">{hunter?.displayName ?? "1 player"}</p>
                  </div>
                  <div>
                    <p className="text-muted">Runners · {nameOfTeam(runnerSide)}</p>
                    <p className="font-semibold">
                      {runners.length > 0 ? runners.map((p) => p.displayName).join(", ") : "3 players"}
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3 text-center">
                  <div>
                    <p className="text-xs text-muted">Cues</p>
                    <p className="font-mono text-4xl font-extrabold">
                      {turn.cues}/{match.cuesPerTurn}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-muted">Caught</p>
                    <p className="font-mono text-4xl font-extrabold">
                      {turn.catches}/{RUNNERS}
                    </p>
                  </div>
                </div>

                {turn.done ? (
                  <div className="space-y-3 text-center">
                    <p className="text-lg font-bold">
                      {nameOfTeam(hunterSide)} caught {turn.catches} · {nameOfTeam(runnerSide)}{" "}
                      kept {RUNNERS - turn.catches} runner(s)
                    </p>
                    <div className="flex justify-center gap-2">
                      <Button variant="secondary" onClick={() => update(undo)}>
                        Undo
                      </Button>
                      {match.current < match.turns.length - 1 && (
                        <Button
                          onClick={() => {
                            setAutoCue(false);
                            update(nextTurn);
                          }}
                        >
                          Next turn · swap roles
                        </Button>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <p className="text-center text-xs text-muted">
                      Start: hunter in the centre, runners in 3 corners. Feint before the cue,
                      move once on it, no diagonals.
                    </p>

                    {autoCue ? (
                      <Button variant="secondary" fullWidth onClick={() => setAutoCue(false)}>
                        Pause the beat
                      </Button>
                    ) : (
                      <div className="flex gap-2">
                        <Button className="flex-1" onClick={startBeat}>
                          ▶ Phone beat every {cueSeconds} s
                        </Button>
                        <Button
                          variant="secondary"
                          onClick={() =>
                            setCueSeconds(
                              CUE_INTERVALS[(CUE_INTERVALS.indexOf(cueSeconds) + 1) % CUE_INTERVALS.length],
                            )
                          }
                        >
                          {cueSeconds} s
                        </Button>
                      </div>
                    )}

                    <div className="grid grid-cols-2 gap-2">
                      <Button
                        variant="secondary"
                        className="py-5 text-base"
                        disabled={autoCue}
                        onClick={() => update(cue)}
                      >
                        Cue given (+1)
                      </Button>
                      <Button className="py-5 text-base" onClick={() => update(addCatch)}>
                        🎯 Caught!
                      </Button>
                    </div>

                    <div className="flex flex-wrap justify-center gap-2">
                      <Button variant="secondary" onClick={() => update(replayCue)}>
                        Disputed · replay cue
                      </Button>
                      <Button variant="secondary" onClick={() => update(undo)}>
                        Undo
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setAutoCue(false);
                          update(endTurn);
                        }}
                      >
                        Stop turn
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
                  Saving records the official result ({teamA.teamName} {score.A} – {score.B}{" "}
                  {teamB.teamName}, out of 24).
                </p>
                <div className="flex justify-center gap-2">
                  <Button variant="secondary" disabled={busy} onClick={() => update(undo)}>
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
