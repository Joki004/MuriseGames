"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { Badge, Card } from "@/component/ui";

import {
  EventGameController,
  type EventGameResponse,
} from "@/modules/Event/EventGame";
import { useEventSnapshot } from "@/modules/Event/Live";
import {
  ScoringController,
  type ScoreboardResponse,
} from "@/modules/Event/Scoring";
import { EventController, type EventDetailResponse } from "@/modules/Event";
function formatMs(ms: number) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

export function TvScreenView() {
  const { eventId } = useParams<{ eventId: string }>();
  const { snapshot, clockOffsetMs } = useEventSnapshot(eventId);

  const [games, setGames] = useState<EventGameResponse[]>([]);
  const [scoreboard, setScoreboard] = useState<ScoreboardResponse | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [event, setEvent] = useState<EventDetailResponse | null>(null);
 useEffect(() => {
    void Promise.all([
      EventController.getDetail(eventId),
      EventGameController.list(eventId),
    ]).then(([ev, g]) => {
      setEvent(ev);
      setGames(g);
    });
  }, [eventId]);

  useEffect(() => {
    void ScoringController.getScoreboard(eventId)
      .then(setScoreboard)
      .catch(() => setScoreboard(null));
  }, [eventId, snapshot?.version, snapshot?.scoreboardVisibility]);

  useEffect(() => {
    if (!snapshot?.deadlineAt || snapshot.paused) return;
    const id = setInterval(() => setNowMs(Date.now()), 250);
    return () => clearInterval(id);
  }, [snapshot?.deadlineAt, snapshot?.paused]);

  const remainingMs = (() => {
    if (!snapshot) return null;
    if (snapshot.paused && snapshot.pausedRemainingMs != null) {
      return snapshot.pausedRemainingMs;
    }
    if (snapshot.deadlineAt) {
      return Math.max(
        0,
        Date.parse(snapshot.deadlineAt) - (nowMs + clockOffsetMs),
      );
    }
    return null;
  })();

  const activeGame = games.find((g) => g.id === snapshot?.currentEventGameId);

  return (
    <RequireAccount>
      <main className="mx-auto flex min-h-screen max-w-6xl flex-col gap-8 p-6 sm:p-10">
        <header className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
          <div>
            <p className="text-sm uppercase tracking-widest text-muted">
              {event?.event.theme || "Live Event"}
            </p>
            <h1 className="text-3xl font-bold sm:text-5xl">
              {event?.event.theme || "Murise Games"}
            </h1>
          </div>
          <div className="flex gap-2">
            <Badge tone={snapshot?.paused ? "warning" : "accent"}>
              {snapshot?.paused
                ? "PAUSED"
                : (snapshot?.phase ?? "LOBBY").toUpperCase()}
            </Badge>
          </div>
        </header>

        <section className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
          <Card className="flex flex-col items-center justify-center gap-4 p-8 text-center">
            <p className="text-xl font-semibold text-muted">
              {activeGame ? activeGame.name : "Waiting for next activity"}
            </p>
            <p className="font-mono text-7xl font-extrabold tracking-tight sm:text-8xl">
              {remainingMs != null ? formatMs(remainingMs) : "--:--"}
            </p>
            {activeGame?.summary && (
              <p className="max-w-xl text-base text-muted">
                {activeGame.summary}
              </p>
            )}
          </Card>

          <Card className="space-y-4 p-6">
            <div className="flex items-center justify-between">
              <h2 className="text-2xl font-bold">Leaderboard</h2>
              {scoreboard?.visibility === "frozen" && (
                <Badge tone="warning">Frozen</Badge>
              )}
            </div>

            {!scoreboard ||
            scoreboard.hiddenFromViewer ||
            scoreboard.visibility === "hidden" ? (
              <p className="py-8 text-center text-muted">
                Scores are currently hidden by the host.
              </p>
            ) : scoreboard.rows.length === 0 ? (
              <p className="py-8 text-center text-muted">
                No scores posted yet.
              </p>
            ) : (
              <ol className="space-y-3">
                {scoreboard.rows.map((row) => (
                  <li
                    key={row.teamId}
                    className="flex items-center justify-between rounded-[var(--ui-radius-control)] border border-border bg-background/50 px-4 py-3 text-lg"
                  >
                    <div className="flex items-center gap-3">
                      <span className="w-6 font-mono font-bold text-muted">
                        #{row.rank}
                      </span>
                      <span
                        className="h-3.5 w-3.5 rounded-full"
                        style={{ backgroundColor: row.teamColor }}
                      />
                      <span className="font-semibold">{row.teamName}</span>
                    </div>
                    <span className="font-mono font-bold">{row.points}</span>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </section>
      </main>
    </RequireAccount>
  );
}
