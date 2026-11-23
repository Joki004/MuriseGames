"use client";

import { useCallback, useEffect, useState } from "react";
import { useEventDetail } from "@/component/Event/EventShell";
import { FixtureResultButton } from "@/component/Event/Scoring/FixtureResultButton";
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
  RefereeController,
  type RefereeDeskResponse,
} from "@/modules/Event/Referee";

const REFRESH_MS = 15000;

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Referee: matches of the games assigned to me, with Record result buttons. */
export function RefereeDesk() {
  const { event, roles } = useEventDetail();
  const isReferee = roles.includes("REFEREE");

  const [desk, setDesk] = useState<RefereeDeskResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      setDesk(await RefereeController.desk(event.id));
      setError(null);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not load your desk.");
    } finally {
      setLoading(false);
    }
  }, [event.id]);

  useEffect(() => {
    if (!isReferee) return;
    void load();
    // Keep statuses fresh when the host or another referee records a result.
    const id = setInterval(() => void load(), REFRESH_MS);
    return () => clearInterval(id);
  }, [isReferee, load]);

  if (!isReferee) {
    return (
      <Card>
        <Alert tone="info">
          This page is for referees. Ask the organizer to give you the Referee
          role and assign you to games.
        </Alert>
      </Card>
    );
  }

  const fixtures = desk?.fixtures ?? [];
  const toRecord = fixtures.filter(
    (f) => !f.ownTeam && f.status !== "completed" && f.status !== "voided",
  ).length;

  return (
    <Card className="space-y-4">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Referee desk</CardTitle>
            <CardDescription>
              {desk && desk.games.length > 0
                ? `You referee: ${desk.games.map((g) => g.name).join(", ")}`
                : "Record results for the games you are assigned to."}
            </CardDescription>
          </div>
          <div className="flex items-center gap-2">
            {desk && desk.games.length > 0 && (
              <Badge tone={toRecord > 0 ? "warning" : "success"}>
                {toRecord} to record
              </Badge>
            )}
            <Button variant="secondary" onClick={() => void load()}>
              Refresh
            </Button>
          </div>
        </div>
      </CardHeader>

      {error && <Alert tone="error">{error}</Alert>}

      {loading && !desk ? (
        <p className="text-sm text-muted">Loading your matches…</p>
      ) : !desk ? null : desk.games.length === 0 ? (
        <p className="text-sm text-muted">
          You are not assigned to any game yet. The organizer assigns referees
          on the People page.
        </p>
      ) : !desk.schedulePublished ? (
        <p className="text-sm text-muted">
          The schedule isn&apos;t published yet. Your matches appear here once
          the organizer publishes the scoring plan.
        </p>
      ) : fixtures.length === 0 ? (
        <p className="text-sm text-muted">
          No matches are scheduled for your games.
        </p>
      ) : (
        <ul className="space-y-2">
          {fixtures.map((f) => {
            const isBye = f.teams.some((t) => t.side === "bye");
            return (
              <li
                key={f.fixtureId}
                className="flex flex-wrap items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border p-3 text-sm"
              >
                <div>
                  <p className="font-semibold">
                    {f.gameName} · Round {f.roundNumber}
                  </p>
                  <p className="text-xs text-muted">
                    {formatTime(f.startAt)} – {formatTime(f.endAt)}
                    {f.resourceName ? ` · ${f.resourceName}` : ""}
                  </p>
                  <p className="mt-1">
                    {isBye
                      ? `${f.teams[0]?.teamName ?? "Team"} (bye)`
                      : f.teams.map((t) => t.teamName).join(" vs ")}
                  </p>
                </div>

                {f.ownTeam ? (
                  <Badge tone="warning">
                    Your team plays: another referee or the host records this
                  </Badge>
                ) : (
                  <FixtureResultButton
                    eventId={event.id}
                    fixture={f}
                    games={desk.games}
                    canCancel={false}
                    onChanged={() => void load()}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
