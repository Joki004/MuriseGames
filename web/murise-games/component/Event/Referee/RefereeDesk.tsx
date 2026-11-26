"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
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

/** Alibi is judged and recorded live on the Alibi game page, not here. */
function isAlibiGame(game: { name: string; headToHead: boolean; fields: string[] } | undefined) {
  if (!game) return false;
  return (
    /alibi/i.test(game.name) ||
    (!game.headToHead && game.fields.join("|") === "Pair 1|Pair 2")
  );
}

function isClosed(status: string) {
  return status === "completed" || status === "voided";
}

/** Referee: matches of the games assigned to me, with Record result buttons. */
export function RefereeDesk() {
  const { event, roles } = useEventDetail();
  const isReferee = roles.includes("REFEREE");

  const [desk, setDesk] = useState<RefereeDeskResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);

  // State is only set in promise callbacks, never synchronously in the effect body.
  useEffect(() => {
    if (!isReferee) return;
    let active = true;
    RefereeController.desk(event.id)
      .then((data) => {
        if (active) {
          setDesk(data);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Could not load your desk.");
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [isReferee, event.id, tick]);

  // Keep statuses fresh when the host or another referee records a result.
  useEffect(() => {
    if (!isReferee) return;
    const id = setInterval(() => setTick((n) => n + 1), REFRESH_MS);
    return () => clearInterval(id);
  }, [isReferee]);

  const reload = () => setTick((n) => n + 1);

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

  // Matches still to record first, finished ones at the bottom.
  const fixtures = [...(desk?.fixtures ?? [])].sort(
    (a, b) => Number(isClosed(a.status)) - Number(isClosed(b.status)),
  );
  const alibiGame = desk?.games.find((g) => isAlibiGame(g));
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
            <Button variant="secondary" onClick={reload}>
              Refresh
            </Button>
          </div>
        </div>
      </CardHeader>

      {error && <Alert tone="error">{error}</Alert>}

      {alibiGame && (
        <Alert tone="info">
          You judge the Alibi game: run it from the{" "}
          <Link
            href={`/events/${event.id}/alibi-game`}
            className="font-semibold underline"
          >
            Alibi game page
          </Link>
          . Its scores are saved there when you finish.
        </Alert>
      )}

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
            const closed = isClosed(f.status);
            const alibi = isAlibiGame(
              desk.games.find((g) => g.eventGameId === f.eventGameId),
            );
            return (
              <li
                key={f.fixtureId}
                className={[
                  "flex flex-wrap items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border p-3 text-sm",
                  closed ? "opacity-60" : "",
                ].join(" ")}
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
                  <div className="flex flex-wrap items-center gap-2">
                    {f.teamBusy && (
                      <Badge tone="warning">Your team plays at this time</Badge>
                    )}
                    {closed && <Badge tone="success">Recorded</Badge>}
                    {alibi && !closed && (
                      <Link
                        href={`/events/${event.id}/alibi-game`}
                        className="ui-button ui-button--primary"
                      >
                        Judge on Alibi page →
                      </Link>
                    )}
                    <FixtureResultButton
                      eventId={event.id}
                      fixture={f}
                      games={desk.games}
                      canCancel={false}
                      onChanged={reload}
                    />
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}