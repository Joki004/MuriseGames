"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Button, Dialog, Input, Select } from "@/component/ui";
import { StarPlayersButton } from "@/component/Event/PlayerPoints/StarPlayersButton";
import { StarPlayersEditor } from "@/component/Event/PlayerPoints/StarPlayersEditor";
import { DuelTrackerDialog } from "@/component/Event/Duel/DuelTrackerDialog";
import { isDuelGame } from "@/modules/Event/Duel";
import {
  ScoringController,
  type FixtureSettlementResponse,
} from "@/modules/Event/Scoring";
import {
  ScorePreviewApi,
  type ScorePreviewGame,
} from "@/modules/Event/Scoring/score-preview";

/** The fixture fields shared by the live snapshot and the schedule view. */
export interface ResultFixture {
  fixtureId: string;
  eventGameId: string;
  gameName: string;
  roundNumber: number;
  status: string;
  teams: { teamId: string; teamName: string; side: string }[];
}

interface FixtureResultButtonProps {
  eventId: string;
  fixture: ResultFixture;
  /** Called after a result is saved or the fixture is cancelled. */
  onChanged?: () => void;
  /** Game definitions supplied by the caller (referees cannot load the organizer list). */
  games?: ScorePreviewGame[];
  /** Cancelling a match is organizer-only. */
  canCancel?: boolean;
}

// Game definitions rarely change during an event; load them once per event.
const gamesCache = new Map<string, Promise<ScorePreviewGame[]>>();

function loadGames(eventId: string) {
  let cached = gamesCache.get(eventId);
  if (!cached) {
    cached = ScorePreviewApi.games(eventId).catch((err: unknown) => {
      gamesCache.delete(eventId);
      throw err;
    });
    gamesCache.set(eventId, cached);
  }
  return cached;
}

type InputMode = "duel" | "split" | "grid";

function inputModeFor(game: ScorePreviewGame, playingTeams: number): InputMode {
  // Social Scenario is head-to-head, but each team scores on its own: grid.
  if (game.headToHead && game.complementary && playingTeams === 2) {
    return game.maximumPerField === 1 ? "duel" : "split";
  }
  return "grid";
}

export function FixtureResultButton({
  eventId,
  fixture,
  onChanged,
  games: suppliedGames,
  canCancel = true,
}: FixtureResultButtonProps) {
  const playing = fixture.teams.filter((t) => t.side !== "bye");
  const isBye = fixture.teams.some((t) => t.side === "bye");
  const completed = fixture.status === "completed";
  const cancelled = fixture.status === "voided";

  const [open, setOpen] = useState(false);
  const [game, setGame] = useState<ScorePreviewGame | null>(null);
  const [values, setValues] = useState<Record<string, (number | null)[]>>({});
  const [reason, setReason] = useState("");
  const [cancelMode, setCancelMode] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<FixtureSettlementResponse | null>(null);
  // null = still checking; false = this game has no scoring (e.g. Final Rehearsal).
  const [scorable, setScorable] = useState<boolean | null>(null);
  // Grab the Cup style games get a live duel tracker (GM-044).
  const [knownGame, setKnownGame] = useState<ScorePreviewGame | null>(null);
  const [trackerOpen, setTrackerOpen] = useState(false);

  useEffect(() => {
    let active = true;
    const source = suppliedGames
      ? Promise.resolve(suppliedGames)
      : loadGames(eventId);
    source
      .then((games) => {
        const game = games.find((g) => g.eventGameId === fixture.eventGameId);
        if (active) {
          setScorable(Boolean(game && !game.unavailableReason));
          setKnownGame(game ?? null);
        }
      })
      // If the definitions can't be loaded, keep the button: the dialog shows the error.
      .catch(() => {
        if (active) setScorable(true);
      });
    return () => {
      active = false;
    };
  }, [eventId, fixture.eventGameId, suppliedGames]);

  if (isBye) return null;

  if (scorable === false) {
    return <Badge>Not scored</Badge>;
  }

  if (cancelled) {
    return <Badge tone="warning">Cancelled</Badge>;
  }

  async function openDialog() {
    setOpen(true);
    setError(null);
    setSaved(null);
    setCancelMode(false);
    setReason(completed ? "" : "Official result");
    try {
      const games = suppliedGames ?? (await loadGames(eventId));
      const found = games.find((g) => g.eventGameId === fixture.eventGameId);
      if (!found) {
        setError("This game has no scoring definition.");
        return;
      }
      setGame(found);
      setValues(
        Object.fromEntries(
          playing.map((t) => [t.teamId, found.fields.map(() => null)]),
        ),
      );
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not load this game's scoring.",
      );
    }
  }

  function setValue(teamId: string, field: number, value: number | null) {
    setValues((current) => {
      const next = { ...current };
      next[teamId] = [...(next[teamId] ?? [])];
      next[teamId][field] = value;
      return next;
    });
  }

  /** Head-to-head: setting one side also sets the other so each field totals the maximum. */
  function setPair(field: number, firstTeamValue: number) {
    if (!game || playing.length !== 2) return;
    setValue(playing[0].teamId, field, firstTeamValue);
    setValue(playing[1].teamId, field, game.maximumPerField - firstTeamValue);
  }

  function giveAllTo(winnerIndex: 0 | 1) {
    if (!game) return;
    game.fields.forEach((_, field) =>
      setPair(field, winnerIndex === 0 ? game.maximumPerField : 0),
    );
  }

  const mode = game ? inputModeFor(game, playing.length) : "grid";
  const complete =
    game !== null &&
    playing.every((t) => (values[t.teamId] ?? []).every((v) => v !== null));
  const rawMax = game ? game.fields.length * game.maximumPerField : 0;
  const rawTotal = (teamId: string) =>
    (values[teamId] ?? []).reduce<number>((sum, v) => sum + (v ?? 0), 0);

  async function save() {
    if (!game || !complete) return;
    setBusy(true);
    setError(null);
    try {
      const teamValues = Object.fromEntries(
        playing.map((t) => [t.teamId, (values[t.teamId] ?? []).map((v) => v ?? 0)]),
      );
      const result = await ScoringController.recordFixtureResult(
        eventId,
        fixture.fixtureId,
        { teamValues, reason: reason.trim() },
      );
      setSaved(result ?? null);
      onChanged?.();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the result.");
    } finally {
      setBusy(false);
    }
  }

  async function cancelFixture() {
    setBusy(true);
    setError(null);
    try {
      await ScoringController.cancelFixture(eventId, fixture.fixtureId, {
        reason: reason.trim(),
      });
      setOpen(false);
      onChanged?.();
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not cancel this fixture.",
      );
    } finally {
      setBusy(false);
    }
  }

  const teamName = (teamId: string) =>
    fixture.teams.find((t) => t.teamId === teamId)?.teamName ?? "Team";
  const reasonOk = reason.trim().length >= (cancelMode ? 5 : 3);

  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        {completed && <Badge tone="success">Result recorded</Badge>}
        {!completed && knownGame && isDuelGame(knownGame) && playing.length === 2 && (
          <Button onClick={() => setTrackerOpen(true)}>▶ Run duels</Button>
        )}
        <Button
          variant={
            completed || (knownGame && isDuelGame(knownGame)) ? "secondary" : "primary"
          }
          onClick={() => void openDialog()}
        >
          {completed ? "Correct result" : "Record result"}
        </Button>
        {completed && (
          <StarPlayersButton
            eventId={eventId}
            fixtureId={fixture.fixtureId}
            title={`${fixture.gameName} · Round ${fixture.roundNumber}`}
          />
        )}
      </div>

      {trackerOpen && knownGame && playing.length === 2 && (
        <DuelTrackerDialog
          eventId={eventId}
          fixtureId={fixture.fixtureId}
          title={`${fixture.gameName} · Round ${fixture.roundNumber}`}
          teams={[
            { teamId: playing[0].teamId, teamName: playing[0].teamName },
            { teamId: playing[1].teamId, teamName: playing[1].teamName },
          ]}
          duelCount={knownGame.fields.length}
          onClose={() => setTrackerOpen(false)}
          onSaved={onChanged}
        />
      )}

      <Dialog
        open={open}
        onOpenChange={(next) => {
          if (!next && !busy) setOpen(false);
        }}
        busy={busy}
        title={`${fixture.gameName} · Round ${fixture.roundNumber}`}
        description={playing.map((t) => t.teamName).join(" vs ")}
      >
        <div className="mt-4 space-y-4">
          {error && <Alert tone="error">{error}</Alert>}

          {saved ? (
            <div className="space-y-3">
              <Alert tone="success">Result saved. The scoreboard is updated.</Alert>
              <ul className="space-y-1 text-sm">
                {saved.teams.map((t) => (
                  <li key={t.teamId} className="flex justify-between gap-3">
                    <span className="font-medium">{teamName(t.teamId)}</span>
                    <span className="text-muted">
                      {t.fixtureRawScore}/{rawMax} in this match · {fixture.gameName}{" "}
                      total <strong className="text-foreground">{t.displayTotal}</strong>
                    </span>
                  </li>
                ))}
              </ul>
              <div className="space-y-2 border-t border-border pt-4">
                <p className="text-sm font-semibold">★ Star players</p>
                <StarPlayersEditor
                  eventId={eventId}
                  fixtureId={fixture.fixtureId}
                  onClose={() => setOpen(false)}
                />
              </div>
            </div>
          ) : !game ? (
            !error && <p className="text-sm text-muted">Loading…</p>
          ) : game.unavailableReason ? (
            <Alert tone="error">{game.unavailableReason}</Alert>
          ) : cancelMode ? (
            <div className="space-y-3">
              <p className="text-sm">
                Cancel this match if it was never played. It won&apos;t count for
                either team, and it can&apos;t take a result afterwards.
              </p>
              <Input
                label="Why was it cancelled?"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                hint="At least 5 characters."
              />
              <div className="flex flex-wrap gap-3">
                <Button
                  variant="danger"
                  loading={busy}
                  disabled={!reasonOk}
                  onClick={() => void cancelFixture()}
                >
                  Cancel match
                </Button>
                <Button
                  variant="secondary"
                  disabled={busy}
                  onClick={() => {
                    setCancelMode(false);
                    setReason(completed ? "" : "Official result");
                  }}
                >
                  Back
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <p className="text-sm text-muted">{game.inputHint}</p>

              {mode === "duel" && (
                <div className="space-y-2">
                  <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" onClick={() => giveAllTo(0)}>
                      {playing[0].teamName} won all
                    </Button>
                    <Button variant="secondary" onClick={() => giveAllTo(1)}>
                      {playing[1].teamName} won all
                    </Button>
                  </div>
                  {game.fields.map((field, i) => {
                    const winner =
                      values[playing[0].teamId]?.[i] === 1
                        ? 0
                        : values[playing[1].teamId]?.[i] === 1
                          ? 1
                          : null;
                    return (
                      <div
                        key={field}
                        className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--ui-radius-control)] border border-border p-2"
                      >
                        <span className="text-sm font-medium">{field} winner</span>
                        <div className="flex gap-2">
                          {([0, 1] as const).map((side) => (
                            <Button
                              key={side}
                              variant={winner === side ? "primary" : "secondary"}
                              onClick={() => setPair(i, side === 0 ? 1 : 0)}
                            >
                              {playing[side].teamName}
                            </Button>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {mode === "split" && (
                <div className="space-y-2">
                  {game.fields.map((field, i) => {
                    const first = values[playing[0].teamId]?.[i];
                    return (
                      <div
                        key={field}
                        className="grid items-end gap-2 sm:grid-cols-[1fr_auto]"
                      >
                        <Select
                          label={`${field}: ${playing[0].teamName}`}
                          value={first ?? ""}
                          onChange={(e) => {
                            if (e.target.value !== "") {
                              setPair(i, Number(e.target.value));
                            }
                          }}
                        >
                          <option value="">Choose…</option>
                          {Array.from(
                            { length: game.maximumPerField + 1 },
                            (_, v) => (
                              <option key={v} value={v}>
                                {v}
                              </option>
                            ),
                          )}
                        </Select>
                        <p className="pb-2 text-sm text-muted">
                          {playing[1].teamName}:{" "}
                          <strong className="text-foreground">
                            {first == null ? "–" : game.maximumPerField - first}
                          </strong>
                        </p>
                      </div>
                    );
                  })}
                </div>
              )}

              {mode === "grid" && (
                <div className="space-y-4">
                  {playing.map((team) => (
                    <div key={team.teamId} className="space-y-2">
                      <p className="text-sm font-semibold">{team.teamName}</p>
                      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                        {game.fields.map((field, i) => (
                          <Select
                            key={field}
                            label={field}
                            value={values[team.teamId]?.[i] ?? ""}
                            onChange={(e) =>
                              setValue(
                                team.teamId,
                                i,
                                e.target.value === "" ? null : Number(e.target.value),
                              )
                            }
                          >
                            <option value="">–</option>
                            {Array.from(
                              { length: game.maximumPerField + 1 },
                              (_, v) => (
                                <option key={v} value={v}>
                                  {v}
                                </option>
                              ),
                            )}
                          </Select>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <div className="rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3 text-sm">
                {playing.map((t) => (
                  <p key={t.teamId} className="flex justify-between gap-3">
                    <span>{t.teamName}</span>
                    <span className="font-mono">
                      {rawTotal(t.teamId)}/{rawMax}
                    </span>
                  </p>
                ))}
                <p className="mt-1 text-xs text-muted">
                  Converted to points out of 5 when you save.
                </p>
              </div>

              <Input
                label={completed ? "Why are you correcting it?" : "Note"}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                hint={completed ? "Kept in the history with the correction." : undefined}
              />

              <div className="flex flex-wrap gap-3">
                <Button
                  loading={busy}
                  disabled={!complete || !reasonOk}
                  onClick={() => void save()}
                >
                  {completed ? "Save correction" : "Save result"}
                </Button>
                <Button
                  variant="secondary"
                  disabled={busy}
                  onClick={() => setOpen(false)}
                >
                  Close
                </Button>
                {!completed && canCancel && (
                  <Button
                    variant="danger"
                    disabled={busy}
                    onClick={() => {
                      setCancelMode(true);
                      setReason("");
                    }}
                  >
                    Match didn&apos;t happen
                  </Button>
                )}
              </div>
            </div>
          )}
        </div>
      </Dialog>
    </>
  );
}
