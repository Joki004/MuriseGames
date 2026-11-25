"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Card, CardHeader, CardTitle, Input } from "@/component/ui";
import { SocialCaseService, type SocialRoleCardView } from "@/modules/Event/Social";

interface SocialPlayerCardProps {
  eventId: string;
  initialRoundNumber?: number;
}

export function SocialPlayerCard({
  eventId,
  initialRoundNumber = 1,
}: SocialPlayerCardProps) {
  const [roundNumber, setRoundNumber] = useState(initialRoundNumber);
  const [card, setCard] = useState<SocialRoleCardView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    SocialCaseService.myRoleCard(eventId, roundNumber)
      .then((res) => {
        if (!cancelled) {
          setError(null);
          setCard(res.data ?? null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load role card.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, roundNumber]);

  return (
    <Card className="space-y-4">
      <CardHeader className="flex flex-row items-center justify-between gap-3">
        <div>
          <CardTitle>Social Scenario Role Card</CardTitle>
          <p className="text-sm text-muted">
            Private briefing scoped strictly to your active round role.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Input
            id="social-round"
            label="Round"
            type="number"
            min={1}
            className="w-24"
            value={roundNumber}
            onChange={(e) => {
              setLoading(true);
              setRoundNumber(Math.max(1, Number(e.target.value) || 1));
            }}
          />
          {card && <Badge>{card.role}</Badge>}
        </div>
      </CardHeader>

      {loading && <p className="text-sm text-muted">Loading role card…</p>}
      {error && <Alert tone="error">{error}</Alert>}

      {!loading && card && (
        <div className="space-y-4">
          {card.label && (
            <div className="flex items-center justify-between text-xs text-muted">
              <span className="font-semibold text-foreground">{card.label}</span>
              {card.versionNumber > 0 && <span>Version {card.versionNumber}</span>}
            </div>
          )}

          <div className="rounded-lg border p-3 space-y-1">
            <h4 className="text-xs font-semibold uppercase tracking-wide text-muted">
              Public Setup
            </h4>
            <p className="text-sm whitespace-pre-wrap">
              {card.publicSetup || "No active scenario assigned for this round yet."}
            </p>
          </div>

          {card.defenderGoals && (
            <div className="rounded-lg border p-3 space-y-2">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-muted">
                Defender Goals
              </h4>
              <ul className="list-disc pl-5 text-sm space-y-1">
                {card.defenderGoals.map((goal, idx) => (
                  <li key={idx}>{goal}</li>
                ))}
              </ul>
            </div>
          )}

          {card.defenderConstraints && (
            <div className="rounded-lg border p-3 space-y-2">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-muted">
                Defender Constraints
              </h4>
              <ul className="list-disc pl-5 text-sm space-y-1">
                {card.defenderConstraints.map((item, idx) => (
                  <li key={idx}>{item}</li>
                ))}
              </ul>
            </div>
          )}

          {card.attackerRedFlags && (
            <div className="rounded-lg border p-3 space-y-2">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-muted">
                Attacker Red Flags
              </h4>
              <ul className="list-disc pl-5 text-sm space-y-1">
                {card.attackerRedFlags.map((flag, idx) => (
                  <li key={idx}>{flag}</li>
                ))}
              </ul>
            </div>
          )}

          {card.attackerBait && (
            <div className="rounded-lg border p-3 space-y-2">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-muted">
                Attacker Bait
              </h4>
              <ul className="list-disc pl-5 text-sm space-y-1">
                {card.attackerBait.map((bait, idx) => (
                  <li key={idx}>{bait}</li>
                ))}
              </ul>
            </div>
          )}

          {card.judgeRubric && (
            <div className="rounded-lg border p-3 space-y-2">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-muted">
                Judge Rubric
              </h4>
              {card.judgeRubric.summary && (
                <p className="text-sm">{card.judgeRubric.summary}</p>
              )}
              {card.judgeRubric.criteria.length > 0 && (
                <ul className="list-disc pl-5 text-sm space-y-1">
                  {card.judgeRubric.criteria.map((c, idx) => (
                    <li key={idx}>{c}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      )}
    </Card>
  );
}