"use client";

import { useEffect, useState } from "react";
import { Alert, Button } from "@/component/ui";
import {
  creditKey,
  PlayerPointsController,
  type FixtureCreditsResponse,
  type PlayerCredit,
} from "@/modules/Event/PlayerPoints";

interface StarPlayersEditorProps {
  eventId: string;
  fixtureId: string;
  onClose?: () => void;
}

/**
 * Referee picks which players earned each part of the match. Personal points
 * only: team scores are not touched. Saving sends the full list, so it can be
 * corrected any time.
 */
export function StarPlayersEditor({ eventId, fixtureId, onClose }: StarPlayersEditorProps) {
  const [data, setData] = useState<FixtureCreditsResponse | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [savedAt, setSavedAt] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    PlayerPointsController.fixture(eventId, fixtureId)
      .then((loaded) => {
        if (cancelled) return;
        setData(loaded);
        setSelected(new Set(loaded.credits.map(creditKey)));
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setLoadError(err instanceof Error ? err.message : "Could not load the players.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, fixtureId]);

  function toggle(segmentIndex: number, participantId: string) {
    const key = creditKey({ segmentIndex, participantId });
    setSavedAt(null);
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  async function save() {
    if (!data) return;
    setBusy(true);
    setError(null);
    try {
      const credits: PlayerCredit[] = Array.from(selected).map((key) => {
        const [segment, participantId] = key.split(":");
        return { segmentIndex: Number(segment), participantId };
      });
      const saved = await PlayerPointsController.save(eventId, fixtureId, credits);
      setData(saved);
      setSelected(new Set(saved.credits.map(creditKey)));
      setSavedAt(Date.now());
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the star players.");
    } finally {
      setBusy(false);
    }
  }

  const doneButton = onClose ? (
    <Button variant="secondary" disabled={busy} onClick={onClose}>
      Done
    </Button>
  ) : null;

  if (loadError || !data || data.unavailableReason) {
    return (
      <div className="space-y-3">
        {loadError ? (
          <Alert tone="error">{loadError}</Alert>
        ) : !data ? (
          <p className="text-sm text-muted">Loading players…</p>
        ) : (
          <Alert tone="info">{data.unavailableReason}</Alert>
        )}
        {doneButton}
      </div>
    );
  }

  const noPlayers = data.teams.every((t) => t.players.length === 0);

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">
        Optional. Tap who won each part. Each tap is 1 star point for that player.
        Team scores don&apos;t change.
      </p>

      {error && <Alert tone="error">{error}</Alert>}
      {savedAt && <Alert tone="success">Star players saved.</Alert>}

      {noPlayers ? (
        <Alert tone="info">Nobody is assigned to these teams yet.</Alert>
      ) : (
        data.segments.map((segment, segmentIndex) => (
          <fieldset
            key={segment}
            className="space-y-2 rounded-[var(--ui-radius-control)] border border-border p-3"
          >
            <legend className="px-1 text-sm font-semibold">{segment}</legend>
            {data.teams.map((team) => (
              <div key={team.teamId} className="space-y-1">
                <p className="flex items-center gap-2 text-xs text-muted">
                  <span
                    aria-hidden="true"
                    className="h-2.5 w-2.5 rounded-full"
                    style={{ backgroundColor: team.teamColor }}
                  />
                  {team.teamName}
                </p>
                <div className="flex flex-wrap gap-2">
                  {team.players.map((player) => {
                    const on = selected.has(
                      creditKey({ segmentIndex, participantId: player.participantId }),
                    );
                    return (
                      <button
                        key={player.participantId}
                        type="button"
                        aria-pressed={on}
                        disabled={busy}
                        onClick={() => toggle(segmentIndex, player.participantId)}
                        className={[
                          "rounded-full border px-3 py-1.5 text-sm transition-colors",
                          on
                            ? "border-accent bg-[var(--accent-soft)] font-semibold text-foreground"
                            : "border-border text-muted hover:text-foreground",
                        ].join(" ")}
                      >
                        {on ? "★ " : ""}
                        {player.displayName}
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </fieldset>
        ))
      )}

      <div className="flex flex-wrap gap-3">
        <Button loading={busy} disabled={noPlayers} onClick={() => void save()}>
          Save star players
        </Button>
        {doneButton}
      </div>
    </div>
  );
}
