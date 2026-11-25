"use client";

import { useCallback } from "react";
import { Badge, Card } from "@/component/ui";
import {
  AlibiGameController,
  formatSeconds,
  remainingMs,
  useAlibiPoll,
  useNow,
  type AlibiPublicRound,
} from "@/modules/Event/Alibi";

const POLL_MS = 2000;

export interface AlibiLive {
  round: AlibiPublicRound | null;
  now: number;
  offsetMs: number;
}

/** Polls the public Alibi round. Call it once per screen and pass the result down. */
export function useAlibiLive(eventId: string): AlibiLive {
  const load = useCallback(() => AlibiGameController.live(eventId), [eventId]);
  const { data, offsetMs } = useAlibiPoll(load, POLL_MS);
  const round = data?.round ?? null;

  const ticking = round?.status === "preparing" || round?.status === "questioning";
  const now = useNow(ticking);

  return { round, now, offsetMs };
}

/** True while the room should watch the Alibi round (not once it is finished). */
export function isAlibiOnStage(round: AlibiPublicRound | null) {
  return round !== null && round.status !== "done";
}

/**
 * The room's view of the Alibi round: the brief, the current question and who has
 * locked, then each question with both answers once an organizer reveals it.
 * Never shows fixed facts, accepted answers or unrevealed answers.
 */
export function AlibiTvCard({ eventId }: { eventId: string }) {
  const live = useAlibiLive(eventId);
  return <AlibiTvPanel live={live} />;
}

/** Compact: the header and the score only, for a finished round on a busy screen. */
export function AlibiTvPanel({
  live,
  compact = false,
}: {
  live: AlibiLive;
  compact?: boolean;
}) {
  const { round, now, offsetMs } = live;

  if (!round) return null;

  if (compact) {
    return (
      <Card className="flex flex-wrap items-center justify-between gap-4 px-6 py-4">
        <div className="flex items-center gap-3">
          <span
            aria-hidden="true"
            className="h-4 w-4 rounded-full"
            style={{ backgroundColor: round.teamColor || "var(--accent)" }}
          />
          <p className="text-lg">
            <span className="text-muted">🕵️ Alibi · Pair {round.pairNumber} · </span>
            <span className="font-bold">{round.teamName}</span>
            <span className="text-muted">
              {" "}
              ({round.playerAName} &amp; {round.playerBName})
            </span>
          </p>
        </div>
        <p className="text-2xl font-bold">
          {round.matches} / {round.questionCount} matches
        </p>
      </Card>
    );
  }

  return (
    <Card className="space-y-6 p-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span
            aria-hidden="true"
            className="h-5 w-5 rounded-full"
            style={{ backgroundColor: round.teamColor || "var(--accent)" }}
          />
          <div>
            <p className="text-sm uppercase tracking-widest text-muted">
              🕵️ Unified Alibi · Pair {round.pairNumber}
            </p>
            <p className="text-3xl font-bold">{round.teamName}</p>
            <p className="text-lg text-muted">
              {round.playerAName} &amp; {round.playerBName}
            </p>
          </div>
        </div>
        <StatusBadge round={round} now={now} offsetMs={offsetMs} />
      </div>

      {round.status === "preparing" && round.brief && (
        <p className="whitespace-pre-line text-2xl leading-relaxed">{round.brief}</p>
      )}

      {round.status === "questioning" && (
        <div className="space-y-4 text-center">
          <p className="text-lg text-muted">
            Question {round.currentQuestion} / {round.questionCount}
          </p>
          <p className="text-4xl font-bold">{round.questionText}</p>
          <div className="flex justify-center gap-3">
            <Badge tone={round.lockedA ? "success" : "neutral"}>
              {round.playerAName} {round.lockedA ? "✓ locked" : "… thinking"}
            </Badge>
            <Badge tone={round.lockedB ? "success" : "neutral"}>
              {round.playerBName} {round.lockedB ? "✓ locked" : "… thinking"}
            </Badge>
          </div>
        </div>
      )}

      {(round.status === "judging" || round.status === "done") && (
        <div className="space-y-4">
          <p className="text-center text-2xl font-semibold">
            {round.matches} / {round.questionCount} matches
            {round.status === "judging" ? " so far" : ""}
          </p>
          {round.revealed.length === 0 ? (
            <p className="text-center text-xl text-muted">
              The judge is comparing their answers…
            </p>
          ) : (
            <ol className="grid gap-3 md:grid-cols-2">
              {round.revealed.map((item) => (
                <li
                  key={item.number}
                  className="rounded-[var(--ui-radius-card)] border border-border bg-background/60 px-4 py-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <p className="text-lg font-semibold">
                      {item.number}. {item.question}
                    </p>
                    <span className="text-2xl" aria-label={item.match ? "Match" : "No match"}>
                      {item.match ? "✅" : "❌"}
                    </span>
                  </div>
                  <p className="mt-1 text-lg">
                    <span className="text-muted">{round.playerAName}:</span> {item.answerA}
                  </p>
                  <p className="text-lg">
                    <span className="text-muted">{round.playerBName}:</span> {item.answerB}
                  </p>
                </li>
              ))}
            </ol>
          )}
        </div>
      )}
    </Card>
  );
}

function StatusBadge({
  round,
  now,
  offsetMs,
}: {
  round: AlibiPublicRound;
  now: number;
  offsetMs: number;
}) {
  if (round.status === "preparing") {
    const left = remainingMs(round.prepEndsAt, now, offsetMs);
    return (
      <Badge tone={left > 0 ? "warning" : "danger"} className="text-2xl">
        {left > 0 ? `Preparing · ${formatSeconds(left)}` : "Separate!"}
      </Badge>
    );
  }
  if (round.status === "questioning") {
    const left = remainingMs(round.questionClosesAt, now, offsetMs);
    return (
      <Badge tone={left > 10_000 ? "warning" : "danger"} className="text-2xl">
        {formatSeconds(left)}
      </Badge>
    );
  }
  if (round.status === "judging") {
    return <Badge tone="accent">Revealing</Badge>;
  }
  if (round.status === "setup") {
    return <Badge>Getting ready</Badge>;
  }
  return <Badge tone="success">Finished</Badge>;
}
