"use client";

import { useCallback, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  EmptyState,
  Select,
} from "@/component/ui";
import {
  AlibiGameController,
  formatSeconds,
  remainingMs,
  useAlibiPoll,
  useNow,
  type AlibiHostView,
  type AlibiRoundHost,
  type AlibiTeamSlot,
} from "@/modules/Event/Alibi";

const POLL_MS = 2000;

type Run = (action: () => Promise<AlibiHostView>) => Promise<void>;

/**
 * Organizers run each team's Alibi slot here: pair 1 then pair 2, each with a fresh
 * case. This screen never shows a case's content, so an organizer can host safely.
 */
export function AlibiHostPanel({ eventId }: { eventId: string }) {
  const load = useCallback(() => AlibiGameController.host(eventId), [eventId]);
  const { data, error, offsetMs, accept } = useAlibiPoll(load, POLL_MS);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const run: Run = useCallback(
    async (action) => {
      setBusy(true);
      setActionError(null);
      try {
        accept(await action());
      } catch (err: unknown) {
        setActionError(err instanceof Error ? err.message : "Something went wrong.");
      } finally {
        setBusy(false);
      }
    },
    [accept],
  );

  if (!data) {
    return error ? <Alert tone="error">{error}</Alert> : null;
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Run Unified Alibi</CardTitle>
          <CardDescription>
            Each team plays in its own slot: pair 1, then pair 2 with a fresh
            case. Preparation 90 s, then 10 questions × 30 s on both phones. A
            case is never played twice, and a team never gets a case one of its
            members knows.
          </CardDescription>
        </CardHeader>
        {actionError && <Alert tone="error">{actionError}</Alert>}
      </Card>

      {!data.planPublished ? (
        <Alert tone="info">
          Publish the scoring plan first (Scoring page): each team&apos;s Alibi
          slot comes from it.
        </Alert>
      ) : data.teams.length === 0 ? (
        <EmptyState
          icon="🕵️"
          title="No Alibi slot in the plan"
          description="Add Unified Alibi to the event's games and publish the schedule and scoring plan."
        />
      ) : (
        data.teams.map((slot) => (
          <TeamSlotCard
            key={slot.fixtureId}
            eventId={eventId}
            slot={slot}
            offsetMs={offsetMs}
            busy={busy}
            run={run}
          />
        ))
      )}
    </div>
  );
}

function TeamSlotCard({
  eventId,
  slot,
  offsetMs,
  busy,
  run,
}: {
  eventId: string;
  slot: AlibiTeamSlot;
  offsetMs: number;
  busy: boolean;
  run: Run;
}) {
  return (
    <Card className="space-y-4">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-3">
            <span
              aria-hidden="true"
              className="h-4 w-4 rounded-full"
              style={{ backgroundColor: slot.teamColor || "var(--accent)" }}
            />
            <CardTitle>{slot.teamName}</CardTitle>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <Badge>{slot.eligibleCases.length} cases available</Badge>
            {slot.sessionId && <Badge tone="accent">{slot.totalMatches}/20 matches</Badge>}
            {slot.recorded && !slot.needsRecording && (
              <Badge tone="success">Result recorded</Badge>
            )}
          </div>
        </div>
      </CardHeader>

      {!slot.sessionId ? (
        <Button
          disabled={busy || slot.fixtureStatus === "voided"}
          onClick={() => void run(() => AlibiGameController.createSession(eventId, slot.fixtureId))}
        >
          Prepare this team&apos;s Alibi slot
        </Button>
      ) : (
        <div className="space-y-4">
          {slot.rounds.map((round) => (
            <RoundCard
              key={`${round.roundId}:${round.status}`}
              eventId={eventId}
              slot={slot}
              round={round}
              offsetMs={offsetMs}
              busy={busy}
              run={run}
            />
          ))}

          {slot.needsRecording && slot.sessionId && (
            <div className="flex flex-wrap items-center gap-3 border-t border-border pt-4">
              <Button
                disabled={busy}
                onClick={() => {
                  const sessionId = slot.sessionId;
                  if (sessionId) {
                    void run(() => AlibiGameController.record(eventId, sessionId));
                  }
                }}
              >
                {slot.recorded ? "Record the corrected result" : "Record the official result"}
              </Button>
              <p className="text-sm text-muted">
                Pair 1 + pair 2 matches → {slot.totalMatches}/20 → leaderboard points ÷ 4.
              </p>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}

function RoundCard({
  eventId,
  slot,
  round,
  offsetMs,
  busy,
  run,
}: {
  eventId: string;
  slot: AlibiTeamSlot;
  round: AlibiRoundHost;
  offsetMs: number;
  busy: boolean;
  run: Run;
}) {
  const ticking = round.status === "preparing" || round.status === "questioning";
  const now = useNow(ticking);
  const otherRunning = slot.rounds.some(
    (r) =>
      r.roundId !== round.roundId &&
      (r.status === "preparing" || r.status === "questioning"),
  );

  return (
    <div className="space-y-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold">
          Pair {round.pairNumber}
          {round.playerAName && round.playerBName
            ? ` · ${round.playerAName} (A) & ${round.playerBName} (B)`
            : ""}
        </p>
        <div className="flex flex-wrap gap-1.5">
          {round.caseLabel && <Badge>{round.caseLabel}</Badge>}
          <Badge tone={round.status === "done" ? "success" : "neutral"}>{round.status}</Badge>
        </div>
      </div>

      {round.status === "setup" && (
        <SetupForm
          eventId={eventId}
          slot={slot}
          round={round}
          busy={busy}
          run={run}
          canStart={!otherRunning}
        />
      )}

      {round.status === "preparing" && (
        <div className="flex flex-wrap items-center gap-3">
          <Badge tone="warning">
            {remainingMs(round.prepEndsAt, now, offsetMs) > 0
              ? `Preparing · ${formatSeconds(remainingMs(round.prepEndsAt, now, offsetMs))}`
              : "Time's up: separate them"}
          </Badge>
          <Button
            disabled={busy}
            onClick={() => void run(() => AlibiGameController.startQuestions(eventId, round.roundId))}
          >
            They are separated: start the questions
          </Button>
        </div>
      )}

      {round.status === "questioning" && (
        <div className="flex flex-wrap items-center gap-3">
          <Badge tone="accent">
            Question {round.currentQuestion}/{round.questionCount}
          </Badge>
          <Badge tone="warning">
            {formatSeconds(remainingMs(round.questionClosesAt, now, offsetMs))}
          </Badge>
          <Badge tone={round.lockedA ? "success" : "neutral"}>A {round.lockedA ? "✓" : "…"}</Badge>
          <Badge tone={round.lockedB ? "success" : "neutral"}>B {round.lockedB ? "✓" : "…"}</Badge>
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => void run(() => AlibiGameController.skip(eventId, round.roundId))}
          >
            Close this question now
          </Button>
        </div>
      )}

      {round.status === "judging" && (
        <div className="flex flex-wrap items-center gap-3">
          <Badge>
            Judged {round.decidedCount}/{round.questionCount}
          </Badge>
          <Badge tone="accent">
            Revealed {round.revealedCount}/{round.questionCount}
          </Badge>
          <Button
            disabled={busy || round.decidedCount <= round.revealedCount}
            onClick={() => void run(() => AlibiGameController.reveal(eventId, round.roundId))}
          >
            Reveal question {round.revealedCount + 1} on the TV
          </Button>
          {round.decidedCount <= round.revealedCount && (
            <p className="text-sm text-muted">
              Waiting for the judge on question {round.revealedCount + 1}.
            </p>
          )}
        </div>
      )}

      {round.status === "done" && (
        <p className="text-sm">
          {round.matches}/{round.questionCount} matches, all revealed.
        </p>
      )}
    </div>
  );
}

function SetupForm({
  eventId,
  slot,
  round,
  busy,
  run,
  canStart,
}: {
  eventId: string;
  slot: AlibiTeamSlot;
  round: AlibiRoundHost;
  busy: boolean;
  run: Run;
  canStart: boolean;
}) {
  const [playerA, setPlayerA] = useState(round.playerA ?? "");
  const [playerB, setPlayerB] = useState(round.playerB ?? "");
  const [caseChoice, setCaseChoice] = useState(round.caseId ?? "random");

  const otherPair = slot.rounds.find((r) => r.roundId !== round.roundId);
  const repeats =
    otherPair &&
    [playerA, playerB].some(
      (p) => p && (p === otherPair.playerA || p === otherPair.playerB),
    );
  const saved =
    round.playerA === playerA &&
    round.playerB === playerB &&
    round.caseId !== null &&
    (caseChoice === "random" || caseChoice === round.caseId);

  const caseOptions = [
    ...(round.caseId && round.caseLabel
      ? [{ caseId: round.caseId, label: `${round.caseLabel} (chosen)` }]
      : []),
    ...slot.eligibleCases,
  ];

  return (
    <div className="space-y-3">
      {slot.members.length < 2 && (
        <Alert tone="info">
          This team needs at least 2 members with an account to play.
        </Alert>
      )}
      <div className="grid gap-3 md:grid-cols-3">
        <Select label="Player A" value={playerA} onChange={(e) => setPlayerA(e.target.value)}>
          <option value="">Choose…</option>
          {slot.members.map((m) => (
            <option key={m.userId} value={m.userId}>
              {m.name}
            </option>
          ))}
        </Select>
        <Select label="Player B" value={playerB} onChange={(e) => setPlayerB(e.target.value)}>
          <option value="">Choose…</option>
          {slot.members
            .filter((m) => m.userId !== playerA)
            .map((m) => (
              <option key={m.userId} value={m.userId}>
                {m.name}
              </option>
            ))}
        </Select>
        <Select
          label="Case"
          hint="Only cases nobody on this team knows."
          value={caseChoice}
          onChange={(e) => setCaseChoice(e.target.value)}
        >
          <option value="random">🎲 Random</option>
          {caseOptions.map((c) => (
            <option key={c.caseId} value={c.caseId}>
              {c.label}
            </option>
          ))}
        </Select>
      </div>

      {repeats && (
        <Alert tone="info">
          Someone plays in both pairs. Only do this when the team is too small.
        </Alert>
      )}

      <div className="flex flex-wrap gap-2">
        <Button
          variant="secondary"
          disabled={busy || !playerA || !playerB || playerA === playerB}
          onClick={() =>
            void run(() =>
              AlibiGameController.setup(eventId, round.roundId, {
                playerA,
                playerB,
                caseId: caseChoice === "random" ? null : caseChoice,
                random: caseChoice === "random",
              }),
            )
          }
        >
          {round.caseId ? "Save changes" : "Save pair and case"}
        </Button>
        <Button
          disabled={busy || !saved || !canStart}
          onClick={() => void run(() => AlibiGameController.start(eventId, round.roundId))}
        >
          Start the 90 s preparation
        </Button>
      </div>
      {!canStart && (
        <p className="text-sm text-muted">The other pair is still playing.</p>
      )}
    </div>
  );
}
