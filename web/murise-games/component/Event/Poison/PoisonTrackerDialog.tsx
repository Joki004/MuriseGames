"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Button, Dialog } from "@/component/ui";
import {
  chooserIndex,
  clearPoisonMatch,
  isFinished,
  isRoundOver,
  loadPoisonMatch,
  loser,
  newPoisonMatch,
  nextRound,
  pick,
  randomCup,
  remainingCups,
  replayRound,
  restartTurnClock,
  roundWinner,
  roundsWon,
  startRound,
  storePoisonMatch,
  teamValues,
  turnRemainingMs,
  turnSide,
  undo,
  type PickResult,
  type PoisonMatchState,
  type Side,
} from "@/modules/Event/Poison";
import {
  PlayerPointsController,
  type FixturePlayer,
} from "@/modules/Event/PlayerPoints";
import { ScoringController } from "@/modules/Event/Scoring";


/** Wall-clock time, read only in event handlers and timers (never while rendering). */
function currentTime() {
  return Date.now();
}

interface PoisonTrackerDialogProps {
  eventId: string;
  fixtureId: string;
  title: string;
  /** The two playing teams, in fixture order: A starts round 1. */
  teams: [{ teamId: string; teamName: string }, { teamId: string; teamName: string }];
  roundCount: number;
  onClose: () => void;
  onSaved?: () => void;
}

/**
 * Poison Game, live (GM-048), for the gamemaster's phone only. The poison is a
 * physical marker (the judge taps safe or poison) or picked secretly by this
 * phone (one tap per cup). It never leaves this device. At the end it records
 * the official result. Kept on this device until saved.
 */
export function PoisonTrackerDialog({
  eventId,
  fixtureId,
  title,
  teams,
  roundCount,
  onClose,
  onSaved,
}: PoisonTrackerDialogProps) {
  const [players, setPlayers] = useState<Record<Side, FixturePlayer[]>>({ A: [], B: [] });
  const [match, setMatch] = useState<PoisonMatchState | null>(null);
  const [selectedCup, setSelectedCup] = useState<{ cup: number; random: boolean } | null>(null);
  const [now, setNow] = useState(currentTime);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const [teamA, teamB] = teams;
  const nameOfTeam = (side: Side) => (side === "A" ? teamA.teamName : teamB.teamName);

  useEffect(() => {
    let cancelled = false;

    function start(loaded: Record<Side, FixturePlayer[]>) {
      if (cancelled) return;
      setPlayers(loaded);
      setMatch(loadPoisonMatch(fixtureId) ?? newPoisonMatch(fixtureId, roundCount));
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
  }, [eventId, fixtureId, roundCount, teamA.teamId, teamB.teamId]);

  const round = match ? match.rounds[match.current] : null;
  const turnEndsAt = round?.turnEndsAt ?? null;

  // 5 s clock for the current chooser; buzz once when it reaches 0.
  useEffect(() => {
    if (turnEndsAt === null) return;
    let buzzed = false;
    const id = setInterval(() => {
      const tick = currentTime();
      setNow(tick);
      if (!buzzed && tick >= turnEndsAt) {
        buzzed = true;
        navigator.vibrate?.(400);
      }
    }, 200);
    return () => clearInterval(id);
  }, [turnEndsAt]);

  function update(change: (current: PoisonMatchState, at: number) => PoisonMatchState) {
    if (!match) return;
    const at = currentTime();
    const next = change(match, at);
    storePoisonMatch(next);
    setNow(at);
    setSelectedCup(null);
    setMatch(next);
  }

  /** A cup was chosen (by the player, or at random by the host). */
  function choose(cup: number, random: boolean) {
    if (!match || !round) return;
    if (round.secretCup !== null) {
      update((m, at) => pick(m, cup, null, at, random));
    } else {
      setSelectedCup({ cup, random });
    }
  }

  function judge(result: PickResult) {
    if (!selectedCup) return;
    const { cup, random } = selectedCup;
    update((m, at) => pick(m, cup, result, at, random));
  }

  async function saveResult() {
    if (!match || !isFinished(match)) return;
    setBusy(true);
    setError(null);
    try {
      const values = teamValues(match);
      const won = roundsWon(match);
      await ScoringController.recordFixtureResult(eventId, fixtureId, {
        teamValues: { [teamA.teamId]: values.A, [teamB.teamId]: values.B },
        reason: `Poison tracker: ${teamA.teamName} ${won.A} – ${won.B} ${teamB.teamName}`,
      });
      clearPoisonMatch(fixtureId);
      setSaved(true);
      onSaved?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the result.");
    } finally {
      setBusy(false);
    }
  }

  const finished = match ? isFinished(match) : false;
  const won = match ? roundsWon(match) : { A: 0, B: 0 };
  const side = round ? turnSide(round) : "A";
  const chooser =
    match && players[side].length > 0
      ? players[side][chooserIndex(match, side) % players[side].length]
      : null;
  const left = round ? turnRemainingMs(round, now) : null;
  const cupsLeft = match && round ? remainingCups(match, round) : [];
  const over = round ? isRoundOver(round) : false;

  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next && !busy) onClose();
      }}
      busy={busy}
      title={title}
      description="Poison Game · gamemaster only, don't show this screen"
    >
      <div className="mt-4 space-y-5">
        {error && <Alert tone="error">{error}</Alert>}

        {!match || !round ? (
          <p className="text-sm text-muted">Loading players…</p>
        ) : saved ? (
          <div className="space-y-3">
            <Alert tone="success">
              Result saved: {teamA.teamName} {won.A} – {won.B} {teamB.teamName}.
            </Alert>
            <Button onClick={onClose}>Done</Button>
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between gap-3 text-lg font-bold">
              <span className="truncate">{teamA.teamName}</span>
              <span className="font-mono">
                {won.A} – {won.B}
              </span>
              <span className="truncate text-right">{teamB.teamName}</span>
            </div>

            <ol className="grid grid-cols-2 gap-2 text-center text-xs">
              {match.rounds.map((r, i) => {
                const w = roundWinner(r);
                return (
                  <li
                    key={i}
                    className={[
                      "rounded-[var(--ui-radius-control)] border px-1 py-1.5",
                      i === match.current && !finished ? "border-accent" : "border-border",
                    ].join(" ")}
                  >
                    <p className="font-semibold">Round {i + 1}</p>
                    <p className="text-muted">{w ? `✓ ${nameOfTeam(w)}` : "—"}</p>
                  </li>
                );
              })}
            </ol>

            {!finished && (
              <div className="space-y-4 rounded-[var(--ui-radius-card)] border border-border p-4">
                <p className="text-center text-sm font-semibold uppercase tracking-wider text-muted">
                  Round {match.current + 1} · {nameOfTeam(round.starter)} starts
                  {round.replays > 0 ? ` · replayed ${round.replays}×` : ""}
                </p>

                {!round.started ? (
                  <div className="space-y-2">
                    <p className="text-center text-xs text-muted">
                      Reshuffle the {match.cups} cups, then choose how the poison is hidden.
                    </p>
                    <Button
                      fullWidth
                      onClick={() =>
                        update((m, at) =>
                          startRound(m, at, randomCup(remainingCups(m, m.rounds[m.current]))),
                        )
                      }
                    >
                      Start · this phone hides the poison
                    </Button>
                    <Button
                      variant="secondary"
                      fullWidth
                      onClick={() => update((m, at) => startRound(m, at, null))}
                    >
                      Start · I placed a physical marker
                    </Button>
                  </div>
                ) : over ? (
                  <div className="space-y-3 text-center">
                    <p className="text-lg font-bold">
                      ☠ Cup {round.picks[round.picks.length - 1]?.cup}:{" "}
                      {nameOfTeam(loser(round) as Side)} took the poison.
                    </p>
                    <p className="text-lg font-bold">
                      {nameOfTeam(roundWinner(round) as Side)} wins round {match.current + 1}
                    </p>
                    <div className="flex justify-center gap-2">
                      <Button variant="secondary" onClick={() => update((m, at) => undo(m, at))}>
                        Undo
                      </Button>
                      {match.current < match.rounds.length - 1 && (
                        <Button onClick={() => update((m) => nextRound(m))}>Next round</Button>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <div className="text-center">
                      <p className="text-lg font-bold">
                        {nameOfTeam(side)}
                        {chooser ? ` · ${chooser.displayName}` : ""} chooses
                      </p>
                      <p
                        className={[
                          "font-mono text-5xl font-extrabold tabular-nums",
                          left === 0 ? "text-danger" : "",
                        ].join(" ")}
                      >
                        {left === null ? "—" : Math.ceil(left / 1000)}
                      </p>
                      {round.secretCup !== null && (
                        <Badge tone="accent">🔒 Poison hidden on this phone</Badge>
                      )}
                    </div>

                    {selectedCup ? (
                      <div className="space-y-2 text-center">
                        <p className="font-semibold">
                          Cup {selectedCup.cup}
                          {selectedCup.random ? " (picked by the host)" : ""}: what was it?
                        </p>
                        <div className="grid grid-cols-2 gap-2">
                          <Button className="py-4" onClick={() => judge("safe")}>
                            Safe
                          </Button>
                          <Button variant="secondary" className="py-4" onClick={() => judge("poison")}>
                            ☠ Poison
                          </Button>
                        </div>
                        <Button variant="secondary" onClick={() => setSelectedCup(null)}>
                          Other cup
                        </Button>
                      </div>
                    ) : (
                      <>
                        <div className="grid grid-cols-3 gap-2">
                          {Array.from({ length: match.cups }, (_, i) => i + 1).map((cup) => {
                            const gone = !cupsLeft.includes(cup);
                            return (
                              <Button
                                key={cup}
                                variant={gone ? "secondary" : "primary"}
                                disabled={gone}
                                className="py-4 text-lg"
                                onClick={() => choose(cup, false)}
                              >
                                {gone ? "✓" : `Cup ${cup}`}
                              </Button>
                            );
                          })}
                        </div>
                        <Button
                          variant={left === 0 ? "primary" : "secondary"}
                          fullWidth
                          onClick={() => {
                            const cup = randomCup(cupsLeft);
                            if (cup !== null) choose(cup, true);
                          }}
                        >
                          No choice in 5 s · host picks a random cup
                        </Button>
                      </>
                    )}

                    <div className="flex flex-wrap justify-center gap-2">
                      <Button variant="secondary" onClick={() => update((m, at) => restartTurnClock(m, at))}>
                        Restart 5 s
                      </Button>
                      <Button variant="secondary" onClick={() => update((m, at) => undo(m, at))}>
                        Undo
                      </Button>
                      <Button variant="secondary" onClick={() => update((m) => replayRound(m))}>
                        Marker seen · replay round
                      </Button>
                    </div>
                  </div>
                )}

                {round.picks.length > 0 && (
                  <p className="text-center text-xs text-muted">
                    {round.picks
                      .map(
                        (p) =>
                          `Cup ${p.cup} ${p.result === "poison" ? "☠" : "✓"} ${nameOfTeam(p.side)}${p.random ? " (random)" : ""}`,
                      )
                      .join(" · ")}
                  </p>
                )}
              </div>
            )}

            {finished && (
              <div className="space-y-3 text-center">
                <Badge tone="success">Both rounds played</Badge>
                <p className="text-sm text-muted">
                  Saving records the official result ({teamA.teamName} {won.A} – {won.B}{" "}
                  {teamB.teamName}).
                </p>
                <div className="flex justify-center gap-2">
                  <Button variant="secondary" disabled={busy} onClick={() => update((m, at) => undo(m, at))}>
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
