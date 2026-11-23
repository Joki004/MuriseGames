"use client";

import { useEffect, useState } from "react";
import { TeamResultsApi, type TeamResultsMode } from "@/modules/Event/Scoring";

const MODES: { mode: TeamResultsMode; label: string; hint: string }[] = [
  {
    mode: "own_results",
    label: "Own results",
    hint: "Each team sees its own matches: opponent, won/lost, score per round.",
  },
  {
    mode: "own_points",
    label: "Own results + points",
    hint: "Also its own points per game and total. Other teams stay hidden.",
  },
  {
    mode: "full_scoreboard",
    label: "Full scoreboard",
    hint: "Every phone shows the full live scoreboard, even while the leaderboard is hidden.",
  },
];

/** Organizer: choose what each team sees about its results on the Player screen. */
export function TeamResultsModeControl({ eventId }: { eventId: string }) {
  const [mode, setMode] = useState<TeamResultsMode | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void TeamResultsApi.getMode(eventId)
      .then((m) => {
        if (active) setMode(m);
      })
      .catch((err: unknown) => {
        if (active) setError(err instanceof Error ? err.message : "Could not load.");
      });
    return () => {
      active = false;
    };
  }, [eventId]);

  async function choose(next: TeamResultsMode) {
    setBusy(true);
    setError(null);
    try {
      setMode(await TeamResultsApi.setMode(eventId, next));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save.");
    } finally {
      setBusy(false);
    }
  }

  const current = MODES.find((m) => m.mode === mode);

  return (
    <div className="mt-3 space-y-1.5 border-t border-zinc-800 pt-3">
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-wider text-zinc-300">
          Team phones show
        </span>
        {MODES.map((m) => (
          <button
            key={m.mode}
            type="button"
            disabled={busy || mode === null || mode === m.mode}
            onClick={() => void choose(m.mode)}
            className={`rounded border px-2 py-0.5 text-xs ${
              mode === m.mode
                ? "border-emerald-600 bg-emerald-950/60 text-emerald-200"
                : "border-zinc-700 text-zinc-300 hover:bg-zinc-800"
            } disabled:opacity-50`}
          >
            {m.label}
          </button>
        ))}
      </div>
      {current && <p className="text-xs text-zinc-400">{current.hint}</p>}
      {error && (
        <p role="alert" className="text-xs text-rose-300">
          {error}
        </p>
      )}
    </div>
  );
}
