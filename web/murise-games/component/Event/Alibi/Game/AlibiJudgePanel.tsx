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
  Input,
} from "@/component/ui";
import {
  AlibiGameController,
  useAlibiPoll,
  type AlibiAnswerView,
  type AlibiJudgePrompt,
  type AlibiJudgeRound,
  type AlibiJudgeView,
} from "@/modules/Event/Alibi";

const POLL_MS = 2000;

/**
 * For organizers and the referees assigned to Alibi. Nobody sees their own team's
 * round. A question appears here only once both answers are locked.
 */
export function AlibiJudgePanel({
  eventId,
  isOrganizer,
}: {
  eventId: string;
  isOrganizer: boolean;
}) {
  const load = useCallback(() => AlibiGameController.judge(eventId), [eventId]);
  const { data, error, accept } = useAlibiPoll(load, POLL_MS);

  if (!data) {
    return error ? <Alert tone="error">{error}</Alert> : null;
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Judge the answers</CardTitle>
          <CardDescription>
            Different words count as a match when they mean the same thing. A
            blank or missing answer, or one that contradicts a fixed fact,
            scores 0. Obvious cases are pre-filled; you can change any of them
            until it is revealed.
          </CardDescription>
        </CardHeader>
        {error && <Alert tone="error">{error}</Alert>}
      </Card>

      {data.rounds.length === 0 ? (
        <EmptyState
          icon="🕵️"
          title="Nothing to judge yet"
          description="Questions appear here as soon as both partners have locked their answers. You never see your own team's round."
        />
      ) : (
        data.rounds.map((round) => (
          <JudgeRoundCard
            key={round.roundId}
            eventId={eventId}
            round={round}
            isOrganizer={isOrganizer}
            onChanged={accept}
          />
        ))
      )}
    </div>
  );
}

function JudgeRoundCard({
  eventId,
  round,
  isOrganizer,
  onChanged,
}: {
  eventId: string;
  round: AlibiJudgeRound;
  isOrganizer: boolean;
  onChanged: (view: AlibiJudgeView) => void;
}) {
  const [showCase, setShowCase] = useState(false);
  const pending = round.prompts.filter((p) => p.verdict === null).length;

  return (
    <Card className="space-y-4">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <CardTitle>
              {round.teamName} · Pair {round.pairNumber}
            </CardTitle>
            <CardDescription>
              {round.caseLabel} · A: {round.playerAName} · B: {round.playerBName}
            </CardDescription>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <Badge>{round.status}</Badge>
            {round.status === "questioning" && (
              <Badge tone="warning">
                Question {round.currentQuestion}/{round.questionCount} open
              </Badge>
            )}
            {pending > 0 && <Badge tone="danger">{pending} to judge</Badge>}
          </div>
        </div>
      </CardHeader>

      <Button variant="secondary" onClick={() => setShowCase((v) => !v)}>
        {showCase ? "Hide the case" : "Show the case (brief, facts, rubric)"}
      </Button>
      {showCase && (
        <div className="space-y-2 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3 text-sm">
          <p className="whitespace-pre-line">{round.brief}</p>
          <ul className="list-disc pl-5">
            {round.fixedFacts.map((fact, i) => (
              <li key={i}>{fact}</li>
            ))}
          </ul>
          {round.judgeRubric && (
            <p className="whitespace-pre-line text-muted">{round.judgeRubric}</p>
          )}
        </div>
      )}

      <ol className="space-y-3">
        {round.prompts.map((prompt) => (
          <PromptRow
            key={prompt.promptId}
            eventId={eventId}
            prompt={prompt}
            round={round}
            isOrganizer={isOrganizer}
            onChanged={onChanged}
          />
        ))}
      </ol>
    </Card>
  );
}

function PromptRow({
  eventId,
  prompt,
  round,
  isOrganizer,
  onChanged,
}: {
  eventId: string;
  prompt: AlibiJudgePrompt;
  round: AlibiJudgeRound;
  isOrganizer: boolean;
  onChanged: (view: AlibiJudgeView) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [correcting, setCorrecting] = useState<boolean | null>(null);
  const [reason, setReason] = useState("");

  async function decide(match: boolean, correctionReason?: string) {
    setBusy(true);
    setError(null);
    try {
      const view = await AlibiGameController.verdict(
        eventId,
        prompt.promptId,
        match,
        correctionReason,
      );
      setCorrecting(null);
      setReason("");
      onChanged(view);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not save the verdict.");
    } finally {
      setBusy(false);
    }
  }

  function choose(match: boolean) {
    if (prompt.verdict === match) return;
    if (prompt.revealed) {
      // Already on the TV: an organizer can still correct it, with a reason.
      setCorrecting(match);
      return;
    }
    void decide(match);
  }

  const locked = prompt.revealed && !isOrganizer;

  return (
    <li className="space-y-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="font-medium">
          {prompt.number}. {prompt.question}
        </p>
        <div className="flex flex-wrap gap-1.5">
          {prompt.verdictSource === "auto" && <Badge>Pre-filled</Badge>}
          {prompt.verdictSource === "correction" && (
            <Badge tone="warning">Corrected</Badge>
          )}
          {prompt.revealed && <Badge tone="accent">On the TV</Badge>}
        </div>
      </div>

      {prompt.validEquivalents.length > 0 && (
        <p className="text-sm text-muted">
          Fixed fact · accepted: {prompt.validEquivalents.join(" · ")}
        </p>
      )}
      {prompt.judgeNote && (
        <p className="text-sm text-muted">Note: {prompt.judgeNote}</p>
      )}

      <div className="grid gap-2 sm:grid-cols-2">
        <AnswerBox label={`A · ${round.playerAName}`} answer={prompt.a} />
        <AnswerBox label={`B · ${round.playerBName}`} answer={prompt.b} />
      </div>

      <div className="flex flex-wrap gap-2">
        <Button
          variant={prompt.verdict === true ? "primary" : "secondary"}
          disabled={busy || locked}
          onClick={() => choose(true)}
        >
          ✓ Match
        </Button>
        <Button
          variant={prompt.verdict === false ? "danger" : "secondary"}
          disabled={busy || locked}
          onClick={() => choose(false)}
        >
          ✗ No match
        </Button>
      </div>

      {correcting !== null && (
        <div className="space-y-2">
          <Input
            label="Why change a revealed verdict?"
            maxLength={300}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <div className="flex gap-2">
            <Button
              loading={busy}
              disabled={reason.trim().length < 3}
              onClick={() => void decide(correcting, reason)}
            >
              Confirm correction
            </Button>
            <Button variant="secondary" onClick={() => setCorrecting(null)}>
              Cancel
            </Button>
          </div>
        </div>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </li>
  );
}

function AnswerBox({ label, answer }: { label: string; answer: AlibiAnswerView }) {
  const away = answer.awayCount > 0;
  return (
    <div className="space-y-1 rounded-[var(--ui-radius-control)] border border-border p-2">
      <p className="text-xs font-semibold text-muted">{label}</p>
      <p className="text-base">
        {answer.missing
          ? "(no answer: time ran out)"
          : answer.text?.trim()
            ? answer.text
            : "(blank)"}
      </p>
      {away && (
        <p className="text-xs font-medium text-accent">
          ⚠️ Left the app {answer.awayCount}× for{" "}
          {Math.round(answer.awayMs / 1000)} s during this question
        </p>
      )}
    </div>
  );
}
