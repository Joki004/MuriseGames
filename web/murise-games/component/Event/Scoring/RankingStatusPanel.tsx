"use client";

import { useEffect, useState } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/component/ui";
import {
  ScoringController,
  type RankingStatusResponse,
} from "@/modules/Event/Scoring";

export function RankingStatusPanel() {
  const { event } = useEventDetail();
  const [status, setStatus] = useState<RankingStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let active = true;

    void ScoringController.getRankingStatus(event.id)
      .then((data) => {
        if (active) {
          setStatus(data);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setStatus(null);
          setError(
            err instanceof Error
              ? err.message
              : "Could not load ranking status.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [event.id, tick]);

  if (loading) {
    return (
      <p className="text-sm text-muted">Checking official ranking status…</p>
    );
  }

  if (!status) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Official standings</CardTitle>
          <CardDescription>
            {error ?? "Publish a scoring plan to enable official standings."}
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card className="space-y-4">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <CardTitle>Official standings (Policy {status.policy})</CardTitle>
            <CardDescription>
              Included games: {status.includedGames.join(", ")} · Max{" "}
              {status.maximumGamePoints} pts (bonuses separate)
            </CardDescription>
          </div>
          <div className="flex items-center gap-2">
            <Badge tone={status.finalRankingAllowed ? "success" : "warning"}>
              {status.finalRankingAllowed
                ? "Final ranking ready"
                : "Provisional"}
            </Badge>
            <Button
              variant="secondary"
              onClick={() => {
                setLoading(true);
                setError(null);
                setTick((n) => n + 1);
              }}
            >
              Refresh
            </Button>
          </div>
        </div>
      </CardHeader>

      {!status.finalRankingAllowed && status.blockers.length > 0 && (
        <Alert tone="info">
          <p className="font-medium">Final ranking is blocked:</p>
          <ul className="mt-1 list-disc pl-5 text-sm">
            {status.blockers.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
        </Alert>
      )}

      <ol className="space-y-2">
        {status.standings.map((row) => (
          <li
            key={row.teamId}
            className="flex items-center justify-between rounded-[var(--ui-radius-control)] border border-border px-4 py-3"
          >
            <div className="flex items-center gap-3">
              <span className="w-6 font-mono font-bold text-muted">
                #{row.rank}
              </span>
              <span className="font-medium">{row.teamName}</span>
            </div>
            <div className="text-right">
              <span className="font-mono text-lg font-bold">
                {row.displayPoints}
              </span>
              <span className="ml-2 font-mono text-xs text-muted">
                ({row.exactNumerator}/{row.exactDenominator})
              </span>
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}
