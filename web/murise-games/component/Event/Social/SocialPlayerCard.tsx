"use client";

import { useEffect, useState } from "react";
import { Badge, Card, CardHeader, CardTitle } from "@/component/ui";
import { SocialCaseService, type SocialRoleCardView } from "@/modules/Event/Social";

interface SocialPlayerCardProps {
  eventId: string;
  /** The Social rounds to look in (2 rounds, decided 25 Nov 2026). */
  rounds?: number[];
}

/** Role cards are assigned by the host during the game: check regularly. */
const REFRESH_MS = 10_000;

const DEFAULT_ROUNDS = [1, 2];

function hasRole(card: SocialRoleCardView | null): card is SocialRoleCardView {
  return card !== null && card.role !== "AUDIENCE" && card.caseId !== null;
}

/**
 * The player's private Social Scenario card. Shows only when this player has a
 * role (the latest round they play in) and appears on its own once the host
 * assigns it; everyone else never sees this card.
 */
export function SocialPlayerCard({
  eventId,
  rounds = DEFAULT_ROUNDS,
}: SocialPlayerCardProps) {
  const [cards, setCards] = useState<(SocialRoleCardView | null)[]>([]);
  const [tick, setTick] = useState(0);
  const roundsKey = rounds.join(",");

  useEffect(() => {
    let cancelled = false;
    const wanted = roundsKey.split(",").map(Number);
    void Promise.all(
      wanted.map((round) =>
        SocialCaseService.myRoleCard(eventId, round)
          .then((res) => res.data ?? null)
          .catch(() => undefined),
      ),
    ).then((loaded) => {
      if (cancelled) return;
      // A failed request keeps what was shown before (no flicker on a bad connection).
      setCards((previous) =>
        loaded.map((card, index) => (card === undefined ? (previous[index] ?? null) : card)),
      );
    });
    return () => {
      cancelled = true;
    };
  }, [eventId, roundsKey, tick]);

  useEffect(() => {
    const id = setInterval(() => {
      if (document.visibilityState === "visible") setTick((n) => n + 1);
    }, REFRESH_MS);
    return () => clearInterval(id);
  }, []);

  let roundIndex = -1;
  cards.forEach((card, index) => {
    if (hasRole(card)) roundIndex = index;
  });

  if (roundIndex < 0) return null;

  const card = cards[roundIndex] as SocialRoleCardView;
  const roundNumber = rounds[roundIndex];

  return (
    <Card className="space-y-4 border-accent">
      <CardHeader className="flex flex-row items-center justify-between gap-3">
        <div>
          <CardTitle>Social Scenario · Round {roundNumber}</CardTitle>
          <p className="text-sm text-muted">
            Private: don&apos;t show this card to anyone.
          </p>
        </div>
        <Badge tone="accent">{card.role}</Badge>
      </CardHeader>

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
    </Card>
  );
}