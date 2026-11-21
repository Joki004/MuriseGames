"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ParticipantController,
  type TeamOption,
} from "@/modules/Event/Participant";
import {
  ScoringController,
  type ScoreCategory,
  type ScoreEntryResponse,
  type ScoreboardResponse,
  type ScoreboardVisibility,
} from "@/modules/Event/Scoring";

interface ScoreboardPanelProps {
  eventId: string;
}

export function ScoreboardPanel({ eventId }: ScoreboardPanelProps) {
  const [board, setBoard] = useState<ScoreboardResponse | null>(null);
  const [entries, setEntries] = useState<ScoreEntryResponse[] | null>(null);
  const [teams, setTeams] = useState<TeamOption[]>([]);

  const [teamId, setTeamId] = useState("");
  const [category, setCategory] =
    useState<Exclude<ScoreCategory, "reversal">>("game_points");
  const [delta, setDelta] = useState("");
  const [reason, setReason] = useState("");

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [scoreboard, ledger, activeTeams] = await Promise.all([
        ScoringController.getScoreboard(eventId),
        ScoringController.listEntriesForOrganizer(eventId),
        ParticipantController.listActiveTeamOptions(eventId),
      ]);
      setBoard(scoreboard);
      setEntries(ledger);
      setTeams(activeTeams);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not load scoreboard."
      );
    } finally {
      setLoading(false);
    }
  }, [eventId]);

  useEffect(() => {
    void load();
  }, [load]);

  const isOrganizer = entries !== null;

  async function handleVisibility(visibility: ScoreboardVisibility) {
    setBusy(true);
    setError(null);
    try {
      const updated = await ScoringController.updateVisibility(
        eventId,
        visibility
      );
      setBoard(updated);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not update visibility."
      );
    } finally {
      setBusy(false);
    }
  }

  async function handleAddScore(e: React.FormEvent) {
    e.preventDefault();
    const numericDelta = Number(delta);
    if (!teamId || Number.isNaN(numericDelta) || !reason.trim()) return;

    setBusy(true);
    setError(null);
    try {
      await ScoringController.addEntry(eventId, {
        teamId,
        category,
        delta: numericDelta,
        reason: reason.trim(),
      });
      setDelta("");
      setReason("");
      await load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not record score.");
    } finally {
      setBusy(false);
    }
  }

  async function handleReverse(entryId: string) {
    setBusy(true);
    setError(null);
    try {
      await ScoringController.reverseEntry(
        eventId,
        entryId,
        "Organizer reversal"
      );
      await load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not reverse entry.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-4 rounded-lg border border-zinc-800 bg-zinc-900/50 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-300">
            Scoreboard
          </h4>
          {board && (
            <span className="rounded bg-zinc-800 px-1.5 py-0.5 text-[11px] text-zinc-300">
              {board.visibility}
            </span>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          {isOrganizer && board && (
            <>
              {(["hidden", "public", "frozen"] as ScoreboardVisibility[]).map(
                (v) => (
                  <button
                    key={v}
                    type="button"
                    disabled={busy || board.visibility === v}
                    onClick={() => void handleVisibility(v)}
                    className={`rounded border px-2 py-0.5 text-xs ${
                      board.visibility === v
                        ? "border-emerald-600 bg-emerald-950/60 text-emerald-200"
                        : "border-zinc-700 text-zinc-300 hover:bg-zinc-800"
                    } disabled:opacity-50`}
                  >
                    {v === "frozen" ? "Freeze" : v}
                  </button>
                )
              )}
            </>
          )}
          <button
            type="button"
            onClick={() => void load()}
            disabled={loading}
            className="rounded border border-zinc-700 px-2 py-0.5 text-xs text-zinc-300 hover:bg-zinc-800 disabled:opacity-40"
          >
            Refresh
          </button>
        </div>
      </div>

      {error && (
        <p role="alert" className="mt-2 text-xs text-rose-300">
          {error}
        </p>
      )}

      {loading && (
        <p className="mt-2 text-xs text-zinc-400">Loading scoreboard…</p>
      )}

      {!loading && board?.hiddenFromViewer && (
        <p className="mt-2 text-xs text-zinc-400">
          Scoreboard is currently hidden by the organizer.
        </p>
      )}

      {!loading && board && !board.hiddenFromViewer && (
        <ul className="mt-3 space-y-1.5">
          {board.rows.length === 0 ? (
            <li className="text-xs text-zinc-500">No teams on scoreboard.</li>
          ) : (
            board.rows.map((row) => (
              <li
                key={row.teamId}
                className="flex items-center justify-between rounded-md border border-zinc-800 bg-zinc-950/70 px-3 py-2 text-sm"
              >
                <div className="flex items-center gap-2">
                  <span className="w-5 font-mono text-xs text-zinc-400">
                    #{row.rank}
                  </span>
                  <span
                    className="h-2.5 w-2.5 rounded-full"
                    style={{ backgroundColor: row.teamColor }}
                  />
                  <span className="font-medium text-zinc-100">
                    {row.teamName}
                  </span>
                </div>
                <span className="font-mono font-semibold text-emerald-300">
                  {Number(row.points)} pts
                </span>
              </li>
            ))
          )}
        </ul>
      )}

      {isOrganizer && (
        <div className="mt-4 border-t border-zinc-800 pt-3">
          <form
            onSubmit={(e) => void handleAddScore(e)}
            className="grid gap-2 text-xs sm:grid-cols-5"
          >
            <select
              required
              value={teamId}
              onChange={(e) => setTeamId(e.target.value)}
              className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            >
              <option value="">Team…</option>
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>

            <select
              value={category}
              onChange={(e) =>
                setCategory(
                  e.target.value as Exclude<ScoreCategory, "reversal">
                )
              }
              className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            >
              <option value="game_points">Game Points</option>
              <option value="bonus">Bonus</option>
              <option value="penalty">Penalty</option>
              <option value="adjustment">Adjustment</option>
            </select>

            <input
              type="number"
              step="any"
              required
              placeholder="Points (+/-)"
              value={delta}
              onChange={(e) => setDelta(e.target.value)}
              className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            />

            <input
              type="text"
              required
              placeholder="Reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            />

            <button
              type="submit"
              disabled={busy}
              className="rounded bg-emerald-600 px-3 py-1 font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
            >
              Add Points
            </button>
          </form>

          {entries && entries.length > 0 && (
            <ul className="mt-3 max-h-48 space-y-1.5 overflow-y-auto">
              {entries.map((entry) => (
                <li
                  key={entry.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded border border-zinc-800/80 bg-zinc-950/50 px-2.5 py-1.5 text-xs"
                >
                  <div>
                    <span className="font-medium text-zinc-200">
                      {entry.teamName}
                    </span>
                    <span
                      className={`ml-2 font-mono ${
                        entry.delta >= 0 ? "text-emerald-300" : "text-rose-300"
                      }`}
                    >
                      {entry.delta >= 0 ? `+${entry.delta}` : entry.delta}
                    </span>
                    <span className="ml-2 text-zinc-400">
                      ({entry.category}) · {entry.reason}
                    </span>
                  </div>

                  {entry.category !== "reversal" && !entry.reversed && (
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => void handleReverse(entry.id)}
                      className="rounded border border-rose-800 px-2 py-0.5 text-[11px] text-rose-200 hover:bg-rose-950/60 disabled:opacity-40"
                    >
                      Reverse
                    </button>
                  )}
                  {entry.reversed && (
                    <span className="text-[11px] text-zinc-500">reversed</span>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}