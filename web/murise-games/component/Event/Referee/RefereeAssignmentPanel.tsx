"use client";

import { useEffect, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/component/ui";
import {
  RefereeController,
  type RefereeBoardResponse,
} from "@/modules/Event/Referee";

interface RefereeAssignmentPanelProps {
  eventId: string;
}

/** Organizer: choose which games each referee may record results for. */
export function RefereeAssignmentPanel({ eventId }: RefereeAssignmentPanelProps) {
  const [board, setBoard] = useState<RefereeBoardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let active = true;
    void RefereeController.board(eventId)
      .then((data) => {
        if (active) {
          setBoard(data);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(
            err instanceof Error ? err.message : "Could not load referees.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [eventId, tick]);

  async function toggle(membershipId: string, eventGameId: string) {
    if (!board) return;
    const key = `${membershipId}:${eventGameId}`;
    const existing = board.assignments.find(
      (a) => a.membershipId === membershipId && a.eventGameId === eventGameId,
    );

    setBusyKey(key);
    setError(null);
    try {
      const updated = existing
        ? await RefereeController.unassign(eventId, existing.id)
        : await RefereeController.assign(eventId, membershipId, eventGameId);
      setBoard(updated);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not update the assignment.",
      );
    } finally {
      setBusyKey(null);
    }
  }

  return (
    <Card className="space-y-4">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Referee assignments</CardTitle>
            <CardDescription>
              Referees can record results only for the games ticked here, and
              never for a match their own team plays in.
            </CardDescription>
          </div>
          <Button
            variant="secondary"
            disabled={loading}
            onClick={() => {
              setLoading(true);
              setTick((n) => n + 1);
            }}
          >
            Refresh
          </Button>
        </div>
      </CardHeader>

      {error && <Alert tone="error">{error}</Alert>}

      {loading && !board ? (
        <p className="text-sm text-muted">Loading referees…</p>
      ) : !board ? null : board.referees.length === 0 ? (
        <p className="text-sm text-muted">
          No referees yet. Give someone the <strong>Referee</strong> role in
          the roles panel, then click Refresh.
        </p>
      ) : board.games.length === 0 ? (
        <p className="text-sm text-muted">
          This event has no games with matches to referee yet.
        </p>
      ) : (
        <ul className="space-y-3">
          {board.referees.map((referee) => (
            <li
              key={referee.membershipId}
              className="space-y-2 rounded-[var(--ui-radius-control)] border border-border p-3"
            >
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold">{referee.displayName}</span>
                {referee.teamName ? (
                  <Badge tone="warning">
                    Plays for {referee.teamName}: can&apos;t record their
                    matches
                  </Badge>
                ) : (
                  <Badge>Neutral</Badge>
                )}
              </div>

              <div className="flex flex-wrap gap-2">
                {board.games.map((game) => {
                  const key = `${referee.membershipId}:${game.eventGameId}`;
                  const assigned = board.assignments.some(
                    (a) =>
                      a.membershipId === referee.membershipId &&
                      a.eventGameId === game.eventGameId,
                  );
                  return (
                    <Button
                      key={game.eventGameId}
                      variant={assigned ? "primary" : "secondary"}
                      loading={busyKey === key}
                      disabled={busyKey !== null}
                      aria-pressed={assigned}
                      onClick={() =>
                        void toggle(referee.membershipId, game.eventGameId)
                      }
                    >
                      {assigned ? "✓ " : ""}
                      {game.name}
                    </Button>
                  );
                })}
              </div>
            </li>
          ))}
        </ul>
      )}

      {board && board.coverage.length > 0 && (
        <section className="space-y-2 border-t border-border pt-4">
          <h3 className="text-sm font-semibold">Coverage</h3>
          <p className="text-xs text-muted">
            Who can record each match. &ldquo;Playing then&rdquo; means their
            team has another match at the same time: they can still record it,
            but may not be able to watch.
          </p>
          <ul className="space-y-1.5">
            {board.coverage.map((row) => {
              const free = row.referees.filter((r) => !r.busy);
              return (
                <li
                  key={row.fixtureId}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--ui-radius-control)] border border-border px-3 py-2 text-sm"
                >
                  <div>
                    <p className="font-medium">
                      {formatTime(row.startAt)} · {row.gameName} · Round{" "}
                      {row.roundNumber}
                    </p>
                    <p className="text-xs text-muted">
                      {row.teams.map((t) => t.teamName).join(" vs ")}
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-1.5">
                    {row.referees.length === 0 ? (
                      <Badge tone="danger">No referee: host records</Badge>
                    ) : (
                      <>
                        {free.length === 0 && (
                          <Badge tone="warning">All referees playing then</Badge>
                        )}
                        {row.referees.map((r) => (
                          <Badge
                            key={r.membershipId}
                            tone={r.busy ? "warning" : "success"}
                          >
                            {r.displayName}
                            {r.busy ? " (playing then)" : ""}
                          </Badge>
                        ))}
                      </>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </Card>
  );
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}
