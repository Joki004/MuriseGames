"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { RevealBoard } from "@/component/Event/Finale/RevealBoard";
import { AlibiPlayerCard } from "@/component/Event/Alibi/Game/AlibiPlayerCard";
import { TeamResultsCard } from "@/component/Event/Scoring/TeamResultsCard";
import { SecretSceneGuessPanel } from "@/component/Event/Guess/SecretSceneGuessPanel";
import { PageShell } from "@/component/Layout/PageShell";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  CardTitle,
} from "@/component/ui";
import { extractApiErrorMessage } from "@/modules/Api/api.service";
import {
  EventGameController,
  type EventGameResponse,
} from "@/modules/Event/EventGame";
import { EventLiveController, useEventSnapshot } from "@/modules/Event/Live";
import { ParticipantController } from "@/modules/Event/Participant";
import { TeamController, type TeamResponse } from "@/modules/Event/Team";
import { UserController } from "@/modules/User";

function formatMs(ms: number) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

export function PlayerScreenView() {
  const { eventId } = useParams<{ eventId: string }>();
  const { snapshot, clockOffsetMs, error, applySnapshot } =
    useEventSnapshot(eventId);

  const [myTeam, setMyTeam] = useState<TeamResponse | null>(null);
  const [games, setGames] = useState<EventGameResponse[]>([]);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    void Promise.all([
      UserController.getProfile(),
      ParticipantController.list(eventId),
      TeamController.list(eventId),
      EventGameController.list(eventId),
    ]).then(([me, participants, teams, eventGames]) => {
      setGames(eventGames);
      const mine = participants.find((p) => p.userId === me.id);
      if (mine?.teamId) {
        setMyTeam(teams.teams.find((t) => t.id === mine.teamId) ?? null);
      }
    });
  }, [eventId]);

  useEffect(() => {
    if (!snapshot?.deadlineAt || snapshot.paused) return;
    const id = setInterval(() => setNowMs(Date.now()), 250);
    return () => clearInterval(id);
  }, [snapshot?.deadlineAt, snapshot?.paused]);

  const remainingMs = (() => {
    if (!snapshot) return null;
    if (snapshot.paused && snapshot.pausedRemainingMs != null) {
      return snapshot.pausedRemainingMs;
    }
    if (snapshot.deadlineAt) {
      return Math.max(
        0,
        Date.parse(snapshot.deadlineAt) - (nowMs + clockOffsetMs),
      );
    }
    return null;
  })();

  const myFixtures = myTeam
    ? (snapshot?.activeFixtures ?? []).filter((f) =>
        f.teams.some((t) => t.teamId === myTeam.id),
      )
    : [];

  const myPlayingFixture =
    myFixtures.find((f) => !f.teams.some((t) => t.side === "bye")) ?? null;

  const activeGame = myPlayingFixture
    ? games.find((g) => g.id === myPlayingFixture.eventGameId)
    : snapshot?.currentWindowStartAt
      ? undefined
      : games.find((g) => g.id === snapshot?.currentEventGameId);

  const isReady = myTeam
    ? (snapshot?.readyTeamIds ?? []).includes(myTeam.id)
    : false;

  async function toggleReady() {
    if (!myTeam) return;
    setBusy(true);
    setActionError(null);
    try {
      const updated = await EventLiveController.setReady(
        eventId,
        myTeam.id,
        !isReady,
      );
      applySnapshot(updated);
    } catch (err: unknown) {
      setActionError(
        extractApiErrorMessage(err, "Could not update team readiness."),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <RequireAccount>
      <PageShell
        title={myTeam ? myTeam.name : "Player screen"}
        description="Live game status and team actions."
        action={
          <Link
            href={`/events/${eventId}`}
            className="ui-button ui-button--secondary"
          >
            Event home
          </Link>
        }
      >
        {(error || actionError) && (
          <Alert tone="error">{actionError ?? error}</Alert>
        )}

        {/* Only shows when this player is in an Alibi pair. */}
        <AlibiPlayerCard eventId={eventId} />

        <Card className="space-y-5 text-center">
          <div className="flex justify-center gap-2">
            <Badge tone={snapshot?.paused ? "warning" : "accent"}>
              {snapshot?.paused ? "Paused" : (snapshot?.phase ?? "lobby")}
            </Badge>
            {activeGame && <Badge>{activeGame.name}</Badge>}
          </div>

          <p className="font-mono text-6xl font-bold tracking-tight">
            {remainingMs != null ? formatMs(remainingMs) : "--:--"}
          </p>

          {myTeam && myFixtures.length > 0 && (
            <div className="mx-auto max-w-md space-y-2 rounded-[var(--ui-radius-control)] border border-border bg-background/60 p-3 text-sm">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted">
                Your Matchup
              </p>
              {myFixtures.map((f) => {
                const isBye = f.teams.some((t) => t.side === "bye");
                const opponents = f.teams.filter(
                  (t) => t.teamId !== myTeam.id && t.side !== "bye",
                );
                return (
                  <div key={f.fixtureId} className="space-y-0.5">
                    <p className="text-xs font-semibold text-accent">
                      {isBye ? "Resting / Bye" : f.gameName}
                    </p>
                    <p className="text-base font-bold">
                      {isBye
                        ? "Bye this round (rest up — you're next!)"
                        : opponents.length > 0
                          ? `vs ${opponents.map((o) => o.teamName).join(" & ")}`
                          : "Solo / All-team station"}
                    </p>
                    <p className="text-xs text-muted">
                      Round {f.roundNumber}
                      {f.resourceName ? ` · ${f.resourceName}` : ""}
                    </p>
                  </div>
                );
              })}
            </div>
          )}

          {myTeam &&
            myFixtures.length === 0 &&
            (snapshot?.activeFixtures?.length ?? 0) > 0 && (
              <div className="mx-auto max-w-md rounded-[var(--ui-radius-control)] border border-border bg-background/60 p-3 text-sm">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted">
                  Short Break
                </p>
                <p className="mt-1 text-base font-bold">
                  You&apos;re resting this round — up next!
                </p>
              </div>
            )}

          {activeGame?.code && (
            <div>
              <Link
                href={`/games/${encodeURIComponent(activeGame.code)}`}
                className="ui-button ui-button--secondary"
              >
                Read {activeGame.name} rules
              </Link>
            </div>
          )}
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Team action</CardTitle>
          </CardHeader>

          {myTeam ? (
            <div className="flex flex-col items-center gap-3">
              <p className="text-sm text-muted">
                Status:{" "}
                <strong>{isReady ? "Ready for host" : "Not ready yet"}</strong>
              </p>
              <Button
                className="w-full py-4 text-lg"
                variant={isReady ? "secondary" : "primary"}
                disabled={busy}
                onClick={() => void toggleReady()}
              >
                {busy
                  ? "Updating…"
                  : isReady
                    ? "Mark team not ready"
                    : "Mark team ready"}
              </Button>
            </div>
          ) : (
            <p className="text-sm text-muted">
              You are not assigned to a team yet. Ask the organizer to place you
              on a team.
            </p>
          )}
        </Card>

        <TeamResultsCard eventId={eventId} snapshotVersion={snapshot?.version} />

        <RevealBoard eventId={eventId} />

        <SecretSceneGuessPanel
          eventId={eventId}
          snapshotVersion={snapshot?.version}
          guessingVersion={snapshot?.guessingVersion}
        />
      </PageShell>
    </RequireAccount>
  );
}