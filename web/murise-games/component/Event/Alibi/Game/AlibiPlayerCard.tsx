"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Textarea,
} from "@/component/ui";
import {
  ALIBI_ANSWER_MAX,
  AlibiGameController,
  formatSeconds,
  remainingMs,
  useAlibiPoll,
  useNow,
  type AlibiPlayerQuestion,
  type AlibiPlayerView,
  type AlibiPlayerRound,
  type AlibiRevealedItem,
} from "@/modules/Event/Alibi";

const POLL_MS = 1500;

/**
 * The Alibi part of a player's phone. Shows nothing unless this player is in a pair.
 * While a question is open it counts how long the player leaves the app; the judge
 * sees it next to the answer.
 */
export function AlibiPlayerCard({ eventId }: { eventId: string }) {
  const load = useCallback(() => AlibiGameController.me(eventId), [eventId]);
  const { data, error, offsetMs, accept } = useAlibiPoll(load, POLL_MS);

  const round = data?.round ?? null;
  if (!round) {
    return error ? <Alert tone="error">{error}</Alert> : null;
  }

  return (
    <Card className="space-y-4">
      <CardHeader>
        <div className="flex flex-wrap items-center gap-2">
          <CardTitle>🕵️ Unified Alibi · Pair {round.pairNumber}</CardTitle>
          <Badge tone="accent">Side {round.side}</Badge>
        </div>
        <CardDescription>Your partner: {round.partnerName}</CardDescription>
      </CardHeader>

      {round.status === "setup" && (
        <Alert tone="info">
          You are playing Alibi with {round.partnerName}. Wait for the host to
          start: you will get the case on this screen.
        </Alert>
      )}

      {round.status === "preparing" && (
        <Preparation round={round} offsetMs={offsetMs} />
      )}

      {round.status === "questioning" && round.question && (
        <QuestionBox
          key={round.question.promptId}
          eventId={eventId}
          question={round.question}
          total={round.questionCount}
          offsetMs={offsetMs}
          onLocked={accept}
        />
      )}

      {(round.status === "judging" || round.status === "done") && (
        <Results round={round} />
      )}

      {error && <Alert tone="error">{error}</Alert>}
    </Card>
  );
}

function Preparation({
  round,
  offsetMs,
}: {
  round: AlibiPlayerRound;
  offsetMs: number;
}) {
  const now = useNow(true);
  const left = remainingMs(round.prepEndsAt, now, offsetMs);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <p className="font-semibold">Prepare together</p>
        <Badge tone={left > 0 ? "warning" : "danger"}>
          {left > 0 ? formatSeconds(left) : "Time's up"}
        </Badge>
      </div>
      {left <= 0 && (
        <Alert tone="info">
          Time&apos;s up: separate from your partner. Questions start in a
          moment.
        </Alert>
      )}
      <p className="whitespace-pre-line text-lg">{round.brief}</p>
      {round.fixedFacts && round.fixedFacts.length > 0 && (
        <div>
          <p className="text-sm font-semibold">Fixed facts (must be correct)</p>
          <ul className="mt-1 list-disc space-y-1 pl-5">
            {round.fixedFacts.map((fact, i) => (
              <li key={i}>{fact}</li>
            ))}
          </ul>
        </div>
      )}
      <p className="text-sm text-muted">
        Agree on the details the questions might ask about: you will answer
        separately and your answers must match.
      </p>
    </div>
  );
}

function QuestionBox({
  eventId,
  question,
  total,
  offsetMs,
  onLocked,
}: {
  eventId: string;
  question: AlibiPlayerQuestion;
  total: number;
  offsetMs: number;
  onLocked: (view: AlibiPlayerView) => void;
}) {
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const now = useNow(!question.locked);
  const left = remainingMs(question.closesAt, now, offsetMs);

  // Latest values for the timer callback and the away tracker.
  const textRef = useRef("");
  const sentRef = useRef(false);
  const awayMsRef = useRef(0);
  const awayCountRef = useRef(0);
  const awaySinceRef = useRef<number | null>(null);

  const submit = useCallback(async () => {
    if (sentRef.current) return;
    sentRef.current = true;
    if (awaySinceRef.current !== null) {
      awayMsRef.current += Date.now() - awaySinceRef.current;
      awaySinceRef.current = null;
    }
    setSending(true);
    setError(null);
    try {
      const view = await AlibiGameController.answer(
        eventId,
        question.promptId,
        textRef.current,
        awayMsRef.current,
        awayCountRef.current,
      );
      onLocked(view);
    } catch (err: unknown) {
      sentRef.current = false;
      setError(err instanceof Error ? err.message : "Could not lock your answer.");
    } finally {
      setSending(false);
    }
  }, [eventId, question.promptId, onLocked]);

  // Counts every time the player leaves the app while the question is open.
  useEffect(() => {
    if (question.locked) return;
    const leave = () => {
      if (awaySinceRef.current === null) {
        awaySinceRef.current = Date.now();
        awayCountRef.current += 1;
      }
    };
    const back = () => {
      if (awaySinceRef.current !== null) {
        awayMsRef.current += Date.now() - awaySinceRef.current;
        awaySinceRef.current = null;
      }
    };
    const onVisibility = () =>
      document.visibilityState === "hidden" ? leave() : back();
    document.addEventListener("visibilitychange", onVisibility);
    window.addEventListener("blur", leave);
    window.addEventListener("focus", back);
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("blur", leave);
      window.removeEventListener("focus", back);
    };
  }, [question.locked]);

  // When time runs out, whatever is typed is locked automatically.
  useEffect(() => {
    if (question.locked) return;
    const id = setInterval(() => {
      if (remainingMs(question.closesAt, Date.now(), offsetMs) <= 0) {
        clearInterval(id);
        void submit();
      }
    }, 250);
    return () => clearInterval(id);
  }, [question.locked, question.closesAt, offsetMs, submit]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold text-muted">
          Question {question.number} / {total}
        </p>
        {!question.locked && (
          <Badge tone={left > 10_000 ? "warning" : "danger"}>
            {formatSeconds(left)}
          </Badge>
        )}
      </div>

      <p className="text-xl font-semibold">{question.text}</p>

      {question.locked ? (
        <Alert tone="success">
          {question.missing
            ? "Time ran out: no answer was recorded for this question."
            : `Locked: “${question.myAnswer || "(blank)"}”. Waiting for the next question…`}
        </Alert>
      ) : (
        <>
          <Textarea
            label="Your answer"
            hint="Stay in the app: leaving it is shown to the judge."
            rows={2}
            maxLength={ALIBI_ANSWER_MAX}
            value={text}
            onChange={(e) => {
              setText(e.target.value);
              textRef.current = e.target.value;
            }}
          />
          <Button
            fullWidth
            loading={sending}
            loadingText="Locking…"
            onClick={() => void submit()}
          >
            Lock my answer
          </Button>
        </>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </div>
  );
}

function Results({ round }: { round: AlibiPlayerRound }) {
  return (
    <div className="space-y-3">
      {round.status === "judging" && round.revealed.length === 0 && (
        <Alert tone="info">
          All answers are locked. The judge is comparing them: watch the TV!
        </Alert>
      )}
      {round.revealed.length > 0 && (
        <>
          <p className="font-semibold">
            {round.matches} / {round.questionCount} matches
            {round.status === "judging" ? " so far" : ""}
          </p>
          <RevealedList items={round.revealed} />
        </>
      )}
    </div>
  );
}

export function RevealedList({ items }: { items: AlibiRevealedItem[] }) {
  return (
    <ol className="space-y-2">
      {items.map((item) => (
        <li
          key={item.number}
          className="rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3 text-sm"
        >
          <div className="flex items-start justify-between gap-2">
            <p className="font-medium">
              {item.number}. {item.question}
            </p>
            <Badge tone={item.match ? "success" : "danger"}>
              {item.match ? "✓ Match" : "✗ No match"}
            </Badge>
          </div>
          <p className="mt-1 text-muted">
            A: {item.answerA} · B: {item.answerB}
          </p>
        </li>
      ))}
    </ol>
  );
}
