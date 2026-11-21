"use client";

import { useEffect, useState } from "react";
import {
  CatalogController,
  type CatalogGameResponse,
  type GameKind,
  type ParticipationMode,
} from "@/modules/Catalog";
import {
  EventGameController,
  type EventGameResponse,
  type EventGameStatus,
} from "@/modules/Event/EventGame";

interface EventGamePanelProps {
  eventId: string;
}

const STATUSES: EventGameStatus[] = ["scheduled", "active", "settled", "voided"];

export function EventGamePanel({ eventId }: EventGamePanelProps) {
  const [lineup, setLineup] = useState<EventGameResponse[]>([]);
  const [catalog, setCatalog] = useState<CatalogGameResponse[]>([]);
  const [selectedDefId, setSelectedDefId] = useState("");
  const [showNewGame, setShowNewGame] = useState(false);

  // Quick-create catalog game fields
  const [newName, setNewName] = useState("");
  const [newKind, setNewKind] = useState<GameKind>("party");
  const [newMode, setNewMode] = useState<ParticipationMode>("all_teams");
  const [newSummary, setNewSummary] = useState("");

  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function fetchLineup() {
      try {
        const [games, catalogGames] = await Promise.all([
          EventGameController.list(eventId),
          CatalogController.list(),
        ]);
        if (active) {
          setLineup(games);
          setCatalog(catalogGames);
          setError(null);
        }
      } catch (err: unknown) {
        if (active) {
          setError(err instanceof Error ? err.message : "Could not load games.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void fetchLineup();

    return () => {
      active = false;
    };
  }, [eventId, revision]);

  function reload() {
    setLoading(true);
    setError(null);
    setRevision((v) => v + 1);
  }

  const availableToAdd = catalog.filter(
    (c) => !lineup.some((g) => g.gameDefinitionId === c.definitionId)
  );

  async function handleAddSelected() {
    if (!selectedDefId) return;
    setBusy(true);
    setError(null);
    try {
      await EventGameController.add(eventId, {
        gameDefinitionId: selectedDefId,
        sortOrder: lineup.length,
      });
      setSelectedDefId("");
      reload();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not add game.");
    } finally {
      setBusy(false);
    }
  }

  async function handleQuickCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const code = newName
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-|-$/g, "");

      const created = await CatalogController.create({
        code,
        name: newName.trim(),
        kind: newKind,
        summary: newSummary.trim() || `${newName.trim()} game`,
        rulesMarkdown: newSummary.trim() || "Organizer-led rules.",
        endGameScoringSummary: "Points awarded by organizer.",
        participationMode: newMode,
        scoringHandler: "manual_points",
      });

      await EventGameController.add(eventId, {
        gameDefinitionId: created.definitionId,
        sortOrder: lineup.length,
      });

      setNewName("");
      setNewSummary("");
      setShowNewGame(false);
      reload();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not create game.");
    } finally {
      setBusy(false);
    }
  }

  async function handleStatusChange(
    eventGameId: string,
    status: EventGameStatus
  ) {
    setBusy(true);
    setError(null);
    try {
      const updated = await EventGameController.update(eventId, eventGameId, {
        status,
      });
      setLineup((prev) =>
        prev.map((g) => (g.id === updated.id ? updated : g))
      );
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not update status.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mt-4 rounded-lg border border-zinc-800 bg-zinc-900/50 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-300">
          Game Lineup ({lineup.length})
        </h4>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setShowNewGame((v) => !v)}
            className="rounded border border-zinc-700 px-2 py-0.5 text-xs text-zinc-200 hover:bg-zinc-800"
          >
            {showNewGame ? "Cancel" : "+ New Game"}
          </button>
          <button
            type="button"
            onClick={reload}
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

      {showNewGame && (
        <form
          onSubmit={(e) => void handleQuickCreate(e)}
          className="mt-3 space-y-2 rounded-md border border-zinc-800 bg-zinc-950/70 p-3 text-xs"
        >
          <div className="grid gap-2 sm:grid-cols-3">
            <input
              type="text"
              required
              placeholder="Game name (e.g. Unified Alibi)"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            />
            <select
              value={newKind}
              onChange={(e) => setNewKind(e.target.value as GameKind)}
              className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            >
              <option value="party">Party</option>
              <option value="competitive">Competitive</option>
              <option value="social_deduction">Social Deduction</option>
              <option value="rehearsal">Rehearsal</option>
            </select>
            <select
              value={newMode}
              onChange={(e) => setNewMode(e.target.value as ParticipationMode)}
              className="rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            >
              <option value="all_teams">All Teams</option>
              <option value="head_to_head">Head to Head</option>
              <option value="team_solo">Team Solo</option>
              <option value="multi_team_pod">Multi-Team Pod</option>
              <option value="individual">Individual</option>
            </select>
          </div>
          <div className="flex gap-2">
            <input
              type="text"
              placeholder="Short summary (optional)"
              value={newSummary}
              onChange={(e) => setNewSummary(e.target.value)}
              className="flex-1 rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-zinc-100"
            />
            <button
              type="submit"
              disabled={busy}
              className="rounded bg-emerald-600 px-3 py-1 font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
            >
              Create & Add
            </button>
          </div>
        </form>
      )}

      {availableToAdd.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-2">
          <select
            value={selectedDefId}
            onChange={(e) => setSelectedDefId(e.target.value)}
            className="flex-1 rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1 text-xs text-zinc-200"
          >
            <option value="">Add from catalog…</option>
            {availableToAdd.map((game) => (
              <option key={game.definitionId} value={game.definitionId}>
                {game.name} ({game.kind})
              </option>
            ))}
          </select>
          <button
            type="button"
            disabled={!selectedDefId || busy}
            onClick={() => void handleAddSelected()}
            className="rounded-md bg-zinc-800 px-3 py-1 text-xs text-zinc-100 hover:bg-zinc-700 disabled:opacity-40"
          >
            Add
          </button>
        </div>
      )}

      {loading && (
        <p className="mt-2 text-xs text-zinc-400">Loading lineup…</p>
      )}

      {!loading && lineup.length === 0 && (
        <p className="mt-2 text-xs text-zinc-500">
          No games in this event yet.
        </p>
      )}

      {!loading && lineup.length > 0 && (
        <ul className="mt-3 space-y-2">
          {lineup.map((game, index) => (
            <li
              key={game.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-zinc-800 bg-zinc-950/70 px-3 py-2 text-sm"
            >
              <div>
                <p className="font-medium text-zinc-100">
                  {index + 1}. {game.name}
                </p>
                <p className="text-xs text-zinc-500">
                  {game.kind} · {game.participationMode}
                </p>
              </div>
              <select
                aria-label={`Status for ${game.name}`}
                value={game.status}
                disabled={busy}
                onChange={(e) =>
                  void handleStatusChange(
                    game.id,
                    e.target.value as EventGameStatus
                  )
                }
                className="rounded-md border border-zinc-700 bg-zinc-900 px-2 py-1 text-xs text-zinc-200"
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}