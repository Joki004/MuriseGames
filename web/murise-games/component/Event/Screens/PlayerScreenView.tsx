"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import {
  RevealBoardView,
  isFinaleOnStage,
  useRevealBoard,
} from "@/component/Event/Finale/RevealBoard";
import {
  PREMIERE_GAME_CODE,
  PerformanceOrderCard,
  REHEARSAL_GAME_CODE,
  performanceStatus,
} from "@/component/Event/Finale/PerformanceOrder";
import { AlibiPlayerCard } from "@/component/Event/Alibi/Game/AlibiPlayerCard";
import { TeamResultsCard } from "@/component/Event/Scoring/TeamResultsCard";
import { SecretSceneGuessPanel } from "@/component/Event/Guess/SecretSceneGuessPanel";
import { SocialPlayerCard } from "@/component/Event/Social/SocialPlayerCard";
import { StarPlayersCard } from "@/component/Event/PlayerPoints/StarPlayersCard";
import { MatchupFaces } from "@/component/Event/Roster/MatchupFaces";
import { RosterAvatar } from "@/component/Event/Roster/RosterAvatar";
import { TeamIdentity } from "@/component/Event/Roster/TeamIdentity";
import { Alert, Badge, Button, Card } from "@/component/ui";
import { extractApiErrorMessage } from "@/modules/Api/api.service";
import {
  EventGameController,
  type EventGameResponse,
} from "@/modules/Event/EventGame";
import { EventLiveController, useEventSnapshot } from "@/modules/Event/Live";
import { ParticipantController } from "@/modules/Event/Participant";
import { rosterTeamFor, useEventRoster } from "@/modules/Event/Roster";
import { TeamController, type TeamResponse } from "@/modules/Event/Team";
import { UserController } from "@/modules/User";

function formatMs(ms: number) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

/**
 * Made for phones, in the order a player needs it: a sticky bar with the team
 * and the clock, then whatever asks for action now (Alibi, Social, guessing),
 * then the current match, then the team and the scores.
 */
export function PlayerScreenView() {
  const { eventId } = useParams<{ eventId: string }>();
  const { snapshot, clockOffsetMs, error, applySnapshot } =
    useEventSnapshot(eventId);

  const [myTeam, setMyTeam] = useState<TeamResponse | null>(null);
  const [myUserId, setMyUserId] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
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
    ])
      .then(([me, participants, teams, eventGames]) => {
        setGames(eventGames);
        setMyUserId(me.id);
        const mine = participants.find((p) => p.userId === me.id);
        if (mine?.teamId) {
          setMyTeam(teams.teams.find((t) => t.id === mine.teamId) ?? null);
        }
      })
      .catch(() => {
        // The live snapshot still works; the team card stays empty.
      })
      .finally(() => setLoaded(true));
  }, [eventId]);

  const { roster } = useEventRoster(eventId);
  const reveals = useRevealBoard(eventId);
  const myRosterTeam = myTeam
    ? rosterTeamFor(roster, myTeam.id, myTeam.name, myTeam.color)
    : null;

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

  const rehearsalNow = activeGame?.code === REHEARSAL_GAME_CODE;
  const premiereNow =
    activeGame?.code === PREMIERE_GAME_CODE ||
    (isFinaleOnStage(reveals) && (snapshot?.activeFixtures?.length ?? 0) === 0);

  const othersPlaying =
    myFixtures.length === 0 && (snapshot?.activeFixtures?.length ?? 0) > 0;

  const activityLabel = myPlayingFixture
    ? myPlayingFixture.gameName
    : activeGame
      ? activeGame.name
      : othersPlaying
        ? "You're on a break"
        : "Waiting for the next activity";

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
      <main
        id="main-content"
        className="mx-auto flex min-h-dvh w-full max-w-md flex-col gap-4 px-3 pb-10"
      >
        {/* ── Sticky bar: always visible while scrolling ── */}
        <header className="sticky top-0 z-20 -mx-3 flex items-center gap-3 border-b border-border bg-background/95 px-3 py-2 backdrop-blur">
          <Link
            href={`/events/${eventId}`}
            aria-label="Back to the event"
            className="rounded-control px-2 py-1 text-lg text-muted hover:text-foreground"
          >
            ←
          </Link>
          {myRosterTeam && (
            <RosterAvatar
              picture={myRosterTeam.picture}
              label={myRosterTeam.name}
              kind="team"
              size="sm"
              color={myRosterTeam.color}
            />
          )}
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold">
              {myRosterTeam?.name ?? "Player screen"}
            </p>
            <p className="truncate text-xs text-muted">{activityLabel}</p>
          </div>
          <div className="flex shrink-0 flex-col items-end">
            <p
              className={[
                "font-mono text-2xl font-bold tabular-nums leading-none",
                remainingMs === 0 && !snapshot?.paused ? "text-danger" : "",
              ].join(" ")}
              aria-label="Time left"
            >
              {remainingMs != null ? formatMs(remainingMs) : "--:--"}
            </p>
            {snapshot?.paused && (
              <Badge tone="warning" className="mt-1">
                Paused
              </Badge>
            )}
          </div>
        </header>

        {(error || actionError) && (
          <Alert tone="error">{actionError ?? error}</Alert>
        )}

        {/* ── Needs you now: these only appear when this player has something to do ── */}
        <AlibiPlayerCard eventId={eventId} />
        <SocialPlayerCard eventId={eventId} />
        <SecretSceneGuessPanel
          eventId={eventId}
          snapshotVersion={snapshot?.version}
          guessingVersion={snapshot?.guessingVersion}
        />

        {/* ── Your current match ── */}
        <Card className="space-y-4">
          {!myTeam ? (
            <p className="text-sm text-muted">
              {loaded
                ? "You are not in a team yet. Ask the organizer to place you in one."
                : "Loading…"}
            </p>
          ) : myFixtures.length > 0 ? (
            myFixtures.map((f) => {
              const isBye = f.teams.some((t) => t.side === "bye");
              const opponents = f.teams.filter(
                (t) => t.teamId !== myTeam.id && t.side !== "bye",
              );
              return (
                <div key={f.fixtureId} className="space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <Badge tone="accent">{isBye ? "Bye" : f.gameName}</Badge>
                    <span className="text-xs text-muted">
                      Round {f.roundNumber}
                      {f.resourceName ? ` · ${f.resourceName}` : ""}
                    </span>
                  </div>
                  {isBye ? (
                    <p className="text-lg font-bold">
                      Bye this round. Rest up, you&apos;re next!
                    </p>
                  ) : f.gameCode === REHEARSAL_GAME_CODE || f.gameCode === PREMIERE_GAME_CODE ? (
                    <p className="text-lg font-bold">
                      All teams together: see the running order below.
                    </p>
                  ) : opponents.length > 0 ? (
                    <MatchupFaces
                      roster={roster}
                      teams={f.teams}
                      size="sm"
                      members="faces"
                      highlightUserId={myUserId}
                    />
                  ) : (
                    <p className="text-lg font-bold">Your team plays alone at this station.</p>
                  )}
                </div>
              );
            })
          ) : (
            <p className="text-lg font-semibold">
              {othersPlaying
                ? "You're on a break. Up next!"
                : "Waiting for the next activity."}
            </p>
          )}

          {activeGame?.code && (
            <Link
              href={`/games/${encodeURIComponent(activeGame.code)}`}
              target="_blank"
              className="ui-button ui-button--secondary w-full"
            >
              {activeGame.name} rules
            </Link>
          )}

          {myTeam && (
            <Button
              className="w-full py-4 text-lg"
              variant={isReady ? "secondary" : "primary"}
              disabled={busy}
              onClick={() => void toggleReady()}
            >
              {busy
                ? "Updating…"
                : isReady
                  ? "✓ Team ready (tap to undo)"
                  : "Mark team ready"}
            </Button>
          )}
          {myTeam && !isReady && (
            <p className="text-center text-xs text-muted">
              Tap when your whole team is at the station: the host starts when
              everyone is ready.
            </p>
          )}
        </Card>

        {(rehearsalNow || premiereNow) && (
          <PerformanceOrderCard
            status={performanceStatus(roster, reveals)}
            myTeamId={myTeam?.id ?? null}
            rehearsal={rehearsalNow && !premiereNow}
          />
        )}

        <RevealBoardView data={reveals} />

        {/* ── Your team and the scores ── */}
        {myRosterTeam && (
          <Card className="flex justify-center py-5">
            <TeamIdentity
              team={myRosterTeam}
              size="md"
              members="names"
              align="center"
              highlightUserId={myUserId}
            />
          </Card>
        )}

        <TeamResultsCard eventId={eventId} snapshotVersion={snapshot?.version} />

        <StarPlayersCard
          eventId={eventId}
          refreshKey={`${snapshot?.version ?? 0}:${snapshot?.scoreboardVisibility ?? ""}`}
          roster={roster}
          size="phone"
        />
      </main>
    </RequireAccount>
  );
}
