"use client";

import { useEffect, useState } from "react";
import { Badge, Card } from "@/component/ui";
import {
  FinaleController,
  type RevealListResponse,
  type RevealView,
} from "@/modules/Event/Finale";

/** Reveals don't change the live snapshot version, so the board polls on its own. */
const POLL_WHILE_CLOSED_MS = 4000;
const POLL_WHILE_OPEN_MS = 20000;

export function stageLabel(stage: number) {
  return stage === 0 ? "before any clue" : `after clue ${stage}`;
}

function formatPoints(points: number) {
  const value = Number(points);
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

interface RevealCardProps {
  reveal: RevealView;
  large?: boolean;
}

/** One revealed title with who guessed it, when, and the points awarded. */
export function RevealCard({ reveal, large = false }: RevealCardProps) {
  return (
    <div className="space-y-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-4">
      <div className="flex items-center gap-2 text-sm text-muted">
        <span
          className="h-3 w-3 rounded-full"
          style={{ backgroundColor: reveal.teamColor }}
        />
        <span className="font-semibold text-foreground">
          {reveal.teamName}
        </span>
        <span>performs</span>
      </div>

      <p className={large ? "text-3xl font-bold sm:text-4xl" : "text-xl font-bold"}>
        {reveal.title ?? "Untitled"}
      </p>

      {reveal.correctGuessers.length === 0 ? (
        <p className="text-sm text-muted">Nobody guessed it.</p>
      ) : (
        <ul className="space-y-1.5">
          {reveal.correctGuessers.map((g) => (
            <li
              key={g.teamId}
              className="flex items-center justify-between gap-3 text-sm"
            >
              <span className="flex items-center gap-2">
                <span
                  className="h-2.5 w-2.5 rounded-full"
                  style={{ backgroundColor: g.teamColor }}
                />
                <span className="font-medium">{g.teamName}</span>
                <span className="text-muted">guessed it {stageLabel(g.stage)}</span>
              </span>
              <span className="font-mono font-bold">
                +{formatPoints(g.points)}
              </span>
            </li>
          ))}
        </ul>
      )}

      {reveal.leaked && (
        <p className="text-sm">
          <Badge tone="warning">Leaked early</Badge>{" "}
          <span className="text-muted">every other team got +2</span>
        </p>
      )}

      {Number(reveal.performerBonus) > 0 && (
        <p className="text-sm">
          <Badge tone="accent">
            +{formatPoints(reveal.performerBonus)} for {reveal.teamName}
          </Badge>{" "}
          <span className="text-muted">
            one point per team that got it after a clue
          </span>
        </p>
      )}
    </div>
  );
}

interface RevealBoardProps {
  eventId: string;
  variant?: "tv" | "player";
}

/** Public board: the API only ever returns titles the host has revealed. */
export function RevealBoard({ eventId, variant = "player" }: RevealBoardProps) {
  const [data, setData] = useState<RevealListResponse | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let active = true;
    void FinaleController.getReveals(eventId)
      .then((res) => {
        if (active) setData(res);
      })
      .catch(() => {
        // Keep the last good board on a transient error.
      });
    return () => {
      active = false;
    };
  }, [eventId, tick]);

  const closed = data?.guessingStatus === "closed";

  useEffect(() => {
    const id = setInterval(
      () => setTick((n) => n + 1),
      closed ? POLL_WHILE_CLOSED_MS : POLL_WHILE_OPEN_MS,
    );
    return () => clearInterval(id);
  }, [closed]);

  if (!data || (!closed && data.reveals.length === 0)) {
    return null;
  }

  const large = variant === "tv";

  return (
    <Card className="space-y-4 p-6">
      <div className="flex items-center justify-between gap-3">
        <h2 className={large ? "text-2xl font-bold" : "text-lg font-bold"}>
          Secret Scene Premiere
        </h2>
        <Badge tone="warning">Guesses locked</Badge>
      </div>

      {data.reveals.length === 0 ? (
        <p className="py-4 text-center text-muted">
          All guesses are locked. Title reveals are coming up.
        </p>
      ) : (
        <div
          className={
            large ? "grid gap-4 sm:grid-cols-2 lg:grid-cols-3" : "space-y-3"
          }
        >
          {[...data.reveals].reverse().map((reveal) => (
            <RevealCard key={reveal.sceneId} reveal={reveal} large={large} />
          ))}
        </div>
      )}
    </Card>
  );
}
