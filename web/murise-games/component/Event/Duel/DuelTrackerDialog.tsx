"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Button, Dialog, Select } from "@/component/ui";
import {
  addEvent,
  attemptScore,
  clearMatch,
  duelWinner,
  duelsWon,
  isFinished,
  loadMatch,
  newMatch,
  nextDuel,
  replayCount,
  setPlayer,
  storeMatch,
  teamValues,
  undo,
  winnerCredits,
  type AttemptEvent,
  type DuelMatchState,
  type Side,
} from "@/modules/Event/Duel";
import {
  PlayerPointsController,
  type FixturePlayer,
} from "@/modules/Event/PlayerPoints";
import { ScoringController } from "@/modules/Event/Scoring";

interface DuelTeam {
  teamId: string;
  teamName: string;
}

interface DuelTrackerDialogProps {
  eventId: string;
  fixtureId: string;
  title: string;
  /** The two playing teams, in fixture order: A then B. */
  teams: [DuelTeam, DuelTeam];
  duelCount: number;
  onClose: () => void;
  onSaved?: () => void;
}

/**
 * Grab the Cup, live (GM-044): the judge taps each attempt, the app keeps the
 * first-to-2 count, moves through the 4 duels with a fair player rotation, then
 * records the official result and credits each duel winner as a star player.
 * Kept on this device until saved, so a refresh loses nothing.
 */
export function DuelTrackerDialog({
  eventId,
  fixtureId,
  title,
  teams,
  duelCount,
  onClose,
  onSaved,
}: DuelTrackerDialogProps) {
  const [players, setPlayers] = useState<Record<Side, FixturePlayer[]>>({ A: [], B: [] });
  const [match, setMatch] = useState<DuelMatchState | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const [teamA, teamB] = teams;

  useEffect(() => {
    let cancelled = false;

    function start(loaded: Record<Side, FixturePlayer[]>) {
      if (cancelled) return;
      setPlayers(loaded);
      setMatch(
        loadMatch(fixtureId) ??
          newMatch(
            fixtureId,
            duelCount,
            loaded.A.map((p) => p.participantId),
            loaded.B.map((p) => p.participantId),
          ),
      );
    }

    PlayerPointsController.fixture(eventId, fixtureId)
      .then((data) => {
        const of = (teamId: string) =>
          data.teams.find((t) => t.teamId === teamId)?.players ?? [];
        start({ A: of(teamA.teamId), B: of(teamB.teamId) });
      })
      // Without the players list the duels still work, just without names.
      .catch(() => start({ A: [], B: [] }));

    return () => {
      cancelled = true;
    };
  }, [eventId, fixtureId, duelCount, teamA.teamId, teamB.teamId]);

  function update(change: (current: DuelMatchState) => DuelMatchState) {
    if (!match) return;
    const next = change(match);
    storeMatch(next);
    setMatch(next);
  }

  const nameOf = (side: Side, participantId: string | null) => {
    const found = players[side].find((p) => p.participantId === participantId);
    return found?.displayName ?? (side === "A" ? teamA.teamName : teamB.teamName);
  };

  async function saveResult() {
    if (!match || !isFinished(match)) return;
    setBusy(true);
    setError(null);
    try {
      const values = teamValues(match);
      const won = duelsWon(match);
      await ScoringController.recordFixtureResult(eventId, fixtureId, {
        teamValues: { [teamA.teamId]: values.A, [teamB.teamId]: values.B },
        reason: `Duel tracker: ${teamA.teamName} ${won.A} – ${won.B} ${teamB.teamName}`,
      });

      const credits = winnerCredits(match);
      if (credits.length > 0) {
        try {
          await PlayerPointsController.save(eventId, fixtureId, credits);
        } catch {
          // The official result is what matters; star players can be fixed with ★ Players.
        }
      }

      clearMatch(fixtureId);
      setSaved(true);
      onSaved?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the result.");
    } finally {
      setBusy(false);
    }
  }

  const duel = match ? match.duels[match.current] : null;
  const score = duel ? attemptScore(duel) : { A: 0, B: 0 };
  const winner = duel ? duelWinner(duel) : null;
  const finished = match ? isFinished(match) : false;
  const won = match ? duelsWon(match) : { A: 0, B: 0 };
  const record = (event: AttemptEvent) => update((m) => addEvent(m, event));

  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next && !busy) onClose();
      }}
      busy={busy}
      title={title}
      description="Grab the Cup · first to 2 attempts wins each duel"
    >
      <div className="mt-4 space-y-5">
        {error && <Alert tone="error">{error}</Alert>}

        {!match ? (
          <p className="text-sm text-muted">Loading players…</p>
        ) : saved ? (
          <div className="space-y-3">
            <Alert tone="success">
              Result saved: {teamA.teamName} {won.A} – {won.B} {teamB.teamName}. Duel
              winners are credited as star players.
            </Alert>
            <Button onClick={onClose}>Done</Button>
          </div>
        ) : (
          <>
            {/* Overall: duels won and the 4 duels at a glance */}
            <div className="flex items-center justify-between gap-3 text-lg font-bold">
              <span className="truncate">{teamA.teamName}</span>
              <span className="font-mono">
                {won.A} – {won.B}
              </span>
              <span className="truncate text-right">{teamB.teamName}</span>
            </div>
            <ol className="grid grid-cols-4 gap-2 text-center text-xs">
              {match.duels.map((d, i) => {
                const w = duelWinner(d);
                return (
                  <li
                    key={i}
                    className={[
                      "rounded-[var(--ui-radius-control)] border px-1 py-1.5",
                      i === match.current && !finished ? "border-accent" : "border-border",
                    ].join(" ")}
                  >
                    <p className="font-semibold">Duel {i + 1}</p>
                    <p className="text-muted">
                      {w ? `✓ ${w === "A" ? teamA.teamName : teamB.teamName}` : "—"}
                    </p>
                  </li>
                );
              })}
            </ol>

            {!finished && duel && (
              <div className="space-y-4 rounded-[var(--ui-radius-card)] border border-border p-4">
                <p className="text-center text-sm font-semibold uppercase tracking-wider text-muted">
                  Duel {match.current + 1} of {match.duels.length}
                  {replayCount(duel) > 0 ? ` · ${replayCount(duel)} replay(s)` : ""}
                </p>

                {(players.A.length > 0 || players.B.length > 0) && (
                  <div className="grid grid-cols-2 gap-3">
                    {(["A", "B"] as Side[]).map((side) => (
                      <Select
                        key={side}
                        label={side === "A" ? teamA.teamName : teamB.teamName}
                        value={(side === "A" ? duel.playerA : duel.playerB) ?? ""}
                        disabled={duel.events.length > 0}
                        onChange={(e) =>
                          update((m) => setPlayer(m, m.current, side, e.target.value || null))
                        }
                      >
                        <option value="">—</option>
                        {players[side].map((p) => (
                          <option key={p.participantId} value={p.participantId}>
                            {p.displayName}
                          </option>
                        ))}
                      </Select>
                    ))}
                  </div>
                )}

                <p className="text-center font-mono text-4xl font-extrabold">
                  {score.A} – {score.B}
                </p>

                {winner ? (
                  <div className="space-y-3 text-center">
                    <p className="text-lg font-bold">
                      🏆 {nameOf(winner, winner === "A" ? duel.playerA : duel.playerB)} wins
                      Duel {match.current + 1}
                    </p>
                    <div className="flex justify-center gap-2">
                      <Button variant="secondary" onClick={() => update(undo)}>
                        Undo
                      </Button>
                      <Button onClick={() => update(nextDuel)}>Next duel</Button>
                    </div>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <p className="text-center text-xs text-muted">Who grabbed the cup on CUP?</p>
                    <div className="grid grid-cols-2 gap-2">
                      {(["A", "B"] as Side[]).map((side) => (
                        <Button
                          key={side}
                          className="py-5 text-base"
                          onClick={() => record({ kind: "win", side })}
                        >
                          {nameOf(side, side === "A" ? duel.playerA : duel.playerB)}
                        </Button>
                      ))}
                      {(["A", "B"] as Side[]).map((side) => (
                        <Button
                          key={`fs-${side}`}
                          variant="secondary"
                          onClick={() => record({ kind: "falseStart", side })}
                        >
                          False start:{" "}
                          {nameOf(side, side === "A" ? duel.playerA : duel.playerB)}
                        </Button>
                      ))}
                    </div>
                    <div className="flex justify-center gap-2">
                      <Button variant="secondary" onClick={() => record({ kind: "replay" })}>
                        Replay (disputed)
                      </Button>
                      <Button
                        variant="secondary"
                        disabled={duel.events.length === 0 && match.current === 0}
                        onClick={() => update(undo)}
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
                <Badge tone="success">All {match.duels.length} duels played</Badge>
                <p className="text-sm text-muted">
                  Saving records the official result ({teamA.teamName} {won.A} –{" "}
                  {won.B} {teamB.teamName}) and credits each duel winner.
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
