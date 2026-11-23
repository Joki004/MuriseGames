"use client";

import { useEffect, useState } from "react";
import { Badge, Card, CardDescription, CardHeader, CardTitle } from "@/component/ui";
import {
  TeamResultsApi,
  type MyTeamResultsResponse,
  type TeamResultsMatch,
} from "@/modules/Event/Scoring";

const REFRESH_MS = 30000;

function formatPoints(value: number | null | undefined) {
  const n = Number(value ?? 0);
  return Number.isInteger(n) ? String(n) : n.toFixed(1);
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function outcomeBadge(match: TeamResultsMatch) {
  if (match.status === "voided") return <Badge tone="warning">Cancelled</Badge>;
  if (!match.outcome) return <Badge>Not recorded yet</Badge>;
  switch (match.outcome) {
    case "win":
      return <Badge tone="success">Won</Badge>;
    case "loss":
      return <Badge tone="danger">Lost</Badge>;
    case "draw":
      return <Badge>Draw</Badge>;
    case "dq":
      return <Badge tone="danger">Disqualified</Badge>;
    default:
      return <Badge tone="accent">Recorded</Badge>;
  }
}

interface TeamResultsCardProps {
  eventId: string;
  /** Live snapshot version: refetch when the host pushes a change. */
  snapshotVersion?: number;
}

/**
 * A team's own results on the Player screen, so a wrong result can be raised
 * with the host straight away. Points and the full scoreboard appear only when
 * the host's "Team phones show" mode allows them.
 */
export function TeamResultsCard({ eventId, snapshotVersion }: TeamResultsCardProps) {
  const [data, setData] = useState<MyTeamResultsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let active = true;
    void TeamResultsApi.mine(eventId)
      .then((res) => {
        if (active) {
          setData(res);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Could not load your results.");
        }
      });
    return () => {
      active = false;
    };
  }, [eventId, snapshotVersion, tick]);

  useEffect(() => {
    const id = setInterval(() => setTick((n) => n + 1), REFRESH_MS);
    return () => clearInterval(id);
  }, []);

  if (error && !data) {
    return (
      <Card>
        <p className="text-sm text-muted">{error}</p>
      </Card>
    );
  }
  if (!data || !data.teamId) return null;

  const played = data.matches.filter((m) => m.outcome || m.status === "voided");

  return (
    <Card className="space-y-4">
      <CardHeader>
        <CardTitle>Our results</CardTitle>
        <CardDescription>
          Something wrong? Show this screen to the host or the referee before
          the next round.
        </CardDescription>
      </CardHeader>

      {/* ---- Points (host mode: own_points or full_scoreboard) ---- */}
      {data.gamePoints && (
        <div className="rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3 text-sm">
          <div className="flex items-center justify-between">
            <span className="font-semibold">Our total</span>
            <span className="font-mono text-lg font-bold">
              {formatPoints(data.totalPoints)}
            </span>
          </div>
          <ul className="mt-2 space-y-1">
            {data.gamePoints.map((g) => (
              <li key={g.eventGameId} className="flex justify-between gap-3 text-muted">
                <span>{g.gameName}</span>
                <span className="font-mono">{formatPoints(g.points)} / 5</span>
              </li>
            ))}
            {Number(data.otherPoints ?? 0) !== 0 && (
              <li className="flex justify-between gap-3 text-muted">
                <span>Secret Scene &amp; host bonuses</span>
                <span className="font-mono">{formatPoints(data.otherPoints)}</span>
              </li>
            )}
          </ul>
        </div>
      )}

      {/* ---- Own matches (always) ---- */}
      {played.length === 0 ? (
        <p className="text-sm text-muted">No results recorded for your team yet.</p>
      ) : (
        <ul className="space-y-2">
          {played.map((m) => (
            <li
              key={m.fixtureId}
              className="space-y-2 rounded-[var(--ui-radius-control)] border border-border p-3 text-sm"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-semibold">
                    {m.gameName} · Round {m.roundNumber}
                  </p>
                  <p className="text-xs text-muted">{formatTime(m.startAt)}</p>
                </div>
                <div className="flex items-center gap-2">
                  {m.corrected && <Badge tone="warning">Corrected</Badge>}
                  {outcomeBadge(m)}
                </div>
              </div>

              {m.outcome && (
                <>
                  <p className="text-base">
                    <strong>{data.teamName}</strong>{" "}
                    <span className="font-mono">
                      {formatPoints(m.rawScore)}
                      {m.opponents.length === 0 && m.rawMax != null
                        ? ` / ${formatPoints(m.rawMax)}`
                        : ""}
                    </span>
                    {m.opponents.map((o) => (
                      <span key={o.teamId}>
                        {" – "}
                        <span className="font-mono">{formatPoints(o.rawScore)}</span>{" "}
                        {o.teamName}
                      </span>
                    ))}
                  </p>

                  {m.fieldLabels.length > 0 && m.values.length === m.fieldLabels.length && (
                    <ul className="flex flex-wrap gap-1.5 text-xs">
                      {m.fieldLabels.map((label, i) => (
                        <li
                          key={label}
                          className="rounded border border-border px-2 py-0.5 text-muted"
                        >
                          {label}: <strong className="text-foreground">{m.values[i]}</strong>
                          {m.opponents.map((o) =>
                            o.values.length === m.fieldLabels.length ? (
                              <span key={o.teamId}>
                                {" – "}
                                {o.values[i]}
                              </span>
                            ) : null,
                          )}
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      {/* ---- Full scoreboard (host mode: full_scoreboard) ---- */}
      {data.scoreboard && data.scoreboard.length > 0 && (
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted">
            Scoreboard
          </p>
          <ol className="space-y-1.5">
            {data.scoreboard.map((row) => (
              <li
                key={row.teamId}
                className={`flex items-center justify-between rounded-[var(--ui-radius-control)] border px-3 py-2 text-sm ${
                  row.teamId === data.teamId
                    ? "border-accent bg-background/70 font-semibold"
                    : "border-border"
                }`}
              >
                <span className="flex items-center gap-2">
                  <span className="w-6 font-mono text-muted">#{row.rank}</span>
                  <span
                    className="h-3 w-3 rounded-full"
                    style={{ backgroundColor: row.teamColor }}
                  />
                  {row.teamName}
                </span>
                <span className="font-mono">{formatPoints(row.points)}</span>
              </li>
            ))}
          </ol>
        </div>
      )}
    </Card>
  );
}
