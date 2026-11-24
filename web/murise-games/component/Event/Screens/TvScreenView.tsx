"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { RevealBoard } from "@/component/Event/Finale/RevealBoard";
import { AlibiTvCard } from "@/component/Event/Alibi/Game/AlibiTvCard";
import {
  guessPointsForStage,
  stageTierLabel,
} from "@/component/Event/Guess/guessTier";
import { Badge, Card } from "@/component/ui";
import { EventController, type EventDetailResponse } from "@/modules/Event";
import {
  ClueController,
  type ReleasedCluesResponse,
} from "@/modules/Event/Clue";
import {
  EventGameController,
  type EventGameResponse,
} from "@/modules/Event/EventGame";
import { useEventSnapshot } from "@/modules/Event/Live";
import {
  ScoringController,
  type ScoreboardResponse,
} from "@/modules/Event/Scoring";
import { TeamController, type TeamResponse } from "@/modules/Event/Team";

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
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [scoreboard, setScoreboard] = useState<ScoreboardResponse | null>(null);
  const [releasedClues, setReleasedClues] =
    useState<ReleasedCluesResponse | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [event, setEvent] = useState<EventDetailResponse | null>(null);

  useEffect(() => {
    void Promise.all([
      EventController.getDetail(eventId),
      EventGameController.list(eventId),
      TeamController.list(eventId),
    ]).then(([ev, g, t]) => {
      setEvent(ev);
      setGames(g);
      setTeams(t.teams);
    });
  }, [eventId]);

  useEffect(() => {
    void ScoringController.getScoreboard(eventId)
      .then(setScoreboard)
      .catch(() => setScoreboard(null));
  }, [eventId, snapshot?.version, snapshot?.scoreboardVisibility]);

  useEffect(() => {
    void ClueController.getReleased(eventId)
      .then(setReleasedClues)
      .catch(() => setReleasedClues(null));
  }, [eventId, snapshot?.version, snapshot?.guessingVersion]);

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
  const activeFixtures = snapshot?.activeFixtures ?? [];
  const distinctGames = Array.from(
    new Set(activeFixtures.map((f) => f.gameName)),
  );

  const restingTeams = (() => {
    if (activeFixtures.length === 0) return [];
    const playingIds = new Set(
      activeFixtures
        .flatMap((f) => f.teams)
        .filter((t) => t.side !== "bye")
        .map((t) => t.teamId),
    );
    return teams.filter((t) => !playingIds.has(t.id));
  })();

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

        {/* Only shows while an Alibi round is being played or revealed. */}
        <AlibiTvCard eventId={eventId} />

        <section className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
          <Card className="flex flex-col items-center justify-center gap-4 p-8 text-center">
            <p className="text-xl font-semibold text-muted">
              {distinctGames.length > 1
                ? "Concurrent Games in Progress"
                : activeGame
                  ? activeGame.name
                  : (distinctGames[0] ?? "Waiting for next activity")}
            </p>
            <p className="font-mono text-7xl font-extrabold tracking-tight sm:text-8xl">
              {remainingMs != null ? formatMs(remainingMs) : "--:--"}
            </p>
            {distinctGames.length <= 1 && activeGame?.summary && (
              <p className="max-w-xl text-base text-muted">
                {activeGame.summary}
              </p>
            )}

            {activeFixtures.length > 0 && (
              <div className="mt-4 w-full space-y-2 border-t border-border pt-4 text-left">
                <p className="text-center text-xs font-semibold uppercase tracking-widest text-muted">
                  Active Matchups
                </p>
                <div className="grid gap-3 sm:grid-cols-2">
                  {activeFixtures.map((f) => {
                    const isBye = f.teams.some((t) => t.side === "bye");
                    return (
                      <div
                        key={f.fixtureId}
                        className="rounded-[var(--ui-radius-control)] border border-border bg-background/60 px-4 py-3"
                      >
                        <div className="flex items-center justify-between gap-2 text-xs text-muted">
                          <Badge>{f.gameName}</Badge>
                          <span>
                            Round {f.roundNumber}
                            {f.resourceName ? ` · ${f.resourceName}` : ""}
                          </span>
                        </div>
                        <p className="mt-1.5 text-lg font-bold">
                          {isBye
                            ? `${f.teams[0]?.teamName ?? "Team"} (Resting / Bye)`
                            : f.teams.map((t) => t.teamName).join(" vs ")}
                        </p>
                      </div>
                    );
                  })}
                </div>

                {restingTeams.length > 0 && (
                  <p className="pt-2 text-center text-sm text-muted">
                    On short break:{" "}
                    <span className="font-semibold text-foreground">
                      {restingTeams.map((t) => t.name).join(", ")}
                    </span>
                  </p>
                )}
              </div>
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

        {releasedClues && releasedClues.currentStage > 0 && (
          <Card className="space-y-4 p-6">
            <div className="flex items-center justify-between">
              <h2 className="text-2xl font-bold">
                Secret Scene Clues · Stage {releasedClues.currentStage} of 3
              </h2>
              <div className="flex flex-wrap items-center gap-2">
                {releasedClues.status !== "closed" && (
                  <Badge tone="accent">
                    Correct guess now:{" "}
                    {guessPointsForStage(releasedClues.currentStage)} pts (
                    {stageTierLabel(releasedClues.currentStage)})
                  </Badge>
                )}
                <Badge
                  tone={
                    releasedClues.status === "closed" ? "warning" : "success"
                  }
                >
                  {releasedClues.status === "closed"
                    ? "Guessing closed"
                    : "Guessing open"}
                </Badge>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {releasedClues.clues.map((c) => (
                <div
                  key={`${c.teamId}-${c.stage}`}
                  className="rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-4"
                >
                  <div className="flex items-center justify-between text-xs text-muted">
                    <span className="font-semibold text-foreground">
                      {c.teamName}
                    </span>
                    <span>Clue {c.stage}</span>
                  </div>
                  <p className="mt-2 text-base">{c.body}</p>
                </div>
              ))}
            </div>
          </Card>
        )}

        <RevealBoard eventId={eventId} variant="tv" />
      </main>
    </RequireAccount>
  );
}