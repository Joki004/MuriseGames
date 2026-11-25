"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import {
  RevealBoardView,
  isFinaleOnStage,
  useRevealBoard,
} from "@/component/Event/Finale/RevealBoard";
import {
  AlibiTvPanel,
  isAlibiOnStage,
  useAlibiLive,
} from "@/component/Event/Alibi/Game/AlibiTvCard";
import { MatchupFaces } from "@/component/Event/Roster/MatchupFaces";
import { StarPlayersCard } from "@/component/Event/PlayerPoints/StarPlayersCard";
import { RosterAvatar } from "@/component/Event/Roster/RosterAvatar";
import {
  TeamIdentity,
  type TeamIdentitySize,
} from "@/component/Event/Roster/TeamIdentity";
import {
  guessPointsForStage,
  stageTierLabel,
} from "@/component/Event/Guess/guessTier";
import { Badge, Card } from "@/component/ui";
import {
  TV_PREVIEW_STATES,
  buildTvPreview,
  parseTvPreview,
  type TvPreviewState,
} from "./tvPreview";
import { EventController, type EventDetailResponse } from "@/modules/Event";
import {
  ClueController,
  type ReleasedCluesResponse,
} from "@/modules/Event/Clue";
import {
  EventGameController,
  type EventGameResponse,
} from "@/modules/Event/EventGame";
import {
  useEventSnapshot,
  type ActiveFixtureView,
} from "@/modules/Event/Live";
import { useEventRoster, type EventRosterResponse } from "@/modules/Event/Roster";
import {
  ScoringController,
  type ScoreboardResponse,
} from "@/modules/Event/Scoring";

const ALIBI_GAME_CODE = "unified-alibi";

function formatMs(ms: number) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

/**
 * The room screen, made for a big screen in full screen (F11): it fits in one
 * view without scrolling. Top: event, current activity and clock. Left, the
 * stage, shows the most important thing right now (finale reveals, a live
 * Alibi round, the matches, or the teams between games). Right: the scores.
 */
export function TvScreenView() {
  const { eventId } = useParams<{ eventId: string }>();
  const { snapshot, clockOffsetMs } = useEventSnapshot(eventId);
  const { roster: liveRoster } = useEventRoster(eventId);
  const liveAlibi = useAlibiLive(eventId);
  const liveReveals = useRevealBoard(eventId);

  // ?preview=<state> shows sample data in this browser only (see tvPreview.ts).
  const previewState = parseTvPreview(useSearchParams().get("preview"));
  const [previewStartedAt] = useState(() => Date.now());

  const [games, setGames] = useState<EventGameResponse[]>([]);
  const [liveScoreboard, setScoreboard] = useState<ScoreboardResponse | null>(null);
  const [liveClues, setReleasedClues] =
    useState<ReleasedCluesResponse | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [event, setEvent] = useState<EventDetailResponse | null>(null);

  useEffect(() => {
    void Promise.all([
      EventController.getDetail(eventId),
      EventGameController.list(eventId),
    ]).then(([ev, g]) => {
      setEvent(ev);
      setGames(g);
    });
  }, [eventId]);

  useEffect(() => {
    void ScoringController.getScoreboard(eventId)
      .then(setScoreboard)
      .catch(() => setScoreboard(null));
  }, [eventId, snapshot?.version, snapshot?.scoreboardVisibility]);

  useEffect(() => {
    void ClueController.getReleased(eventId)
      .then(setReleasedClues)
      .catch(() => setReleasedClues(null));
  }, [eventId, snapshot?.version, snapshot?.guessingVersion]);

  const ticking = previewState !== null || (Boolean(snapshot?.deadlineAt) && !snapshot?.paused);

  useEffect(() => {
    if (!ticking) return;
    const id = setInterval(() => setNowMs(Date.now()), 250);
    return () => clearInterval(id);
  }, [ticking]);

  const preview = previewState
    ? buildTvPreview(previewState, liveRoster, eventId, previewStartedAt, nowMs)
    : null;

  const roster = preview?.roster ?? liveRoster;
  const alibi = preview?.alibi ?? liveAlibi;
  const reveals = preview ? preview.reveals : liveReveals;
  const scoreboard = preview?.scoreboard ?? liveScoreboard;
  const releasedClues = preview ? preview.clues : liveClues;
  const phase = preview?.phase ?? snapshot?.phase ?? "lobby";
  const paused = preview ? false : Boolean(snapshot?.paused);

  const remainingMs = (() => {
    if (preview) {
      return preview.deadlineAt === null ? null : Math.max(0, preview.deadlineAt - nowMs);
    }
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

  const activeGame = preview
    ? undefined
    : games.find((g) => g.id === snapshot?.currentEventGameId);
  const activeFixtures = preview?.fixtures ?? snapshot?.activeFixtures ?? [];
  const playingFixtures = activeFixtures.filter(
    (f) => !f.teams.some((t) => t.side === "bye"),
  );
  const distinctGames = Array.from(new Set(playingFixtures.map((f) => f.gameName)));

  const activityLabel =
    distinctGames.length > 1
      ? `${distinctGames.length} games at the same time`
      : (activeGame?.name ?? distinctGames[0] ?? "Waiting for the next activity");

  const restingTeams = (() => {
    if (activeFixtures.length === 0 || !roster) return [];
    const playingIds = new Set(
      playingFixtures.flatMap((f) => f.teams).map((t) => t.teamId),
    );
    return roster.teams.filter((t) => !playingIds.has(t.teamId));
  })();

  // What is being played right now always wins; the finale board takes the
  // stage only when no match is running (guessing stays closed after the finale).
  // The Alibi board stays on the stage for the whole Alibi slot, so the room
  // still sees all 10 answers after the last reveal (the round is then "done").
  const alibiSlotActive = playingFixtures.some((f) => f.gameCode === ALIBI_GAME_CODE);
  const alibiOnStage =
    alibi.round !== null && (alibiSlotActive || isAlibiOnStage(alibi.round));
  const finaleOnStage =
    !alibiOnStage && playingFixtures.length === 0 && isFinaleOnStage(reveals);
  const showClues =
    !finaleOnStage &&
    releasedClues !== null &&
    releasedClues.currentStage > 0 &&
    releasedClues.status !== "closed";

  // While an Alibi round is live it takes the stage; the other games stay visible below it.
  const otherFixtures = alibiOnStage
    ? playingFixtures.filter((f) => f.gameCode !== ALIBI_GAME_CODE)
    : playingFixtures;

  const eventName = event?.event.name ?? "Murise Games";
  const theme = event?.event.theme;
  const timeUp = remainingMs === 0 && !paused;

  return (
    <RequireAccount>
      {/* Fills the screen; if a state has more than fits (small laptop), the page scrolls instead of cutting it off. */}
      <main
        className={[
          "flex min-h-dvh flex-col gap-4 p-4 xl:gap-6 xl:p-6 2xl:p-8",
          previewState ? "pb-24" : "",
        ].join(" ")}
      >
        {/* ── Top bar: what is happening and how long is left ── */}
        <header className="flex shrink-0 items-center justify-between gap-6 rounded-card border border-border bg-surface px-6 py-4">
          <div className="min-w-0">
            <p className="truncate text-sm uppercase tracking-widest text-muted xl:text-base">
              {theme ? `${eventName} · ${theme}` : eventName}
            </p>
            <p className="truncate text-3xl font-bold xl:text-4xl 2xl:text-5xl">
              {finaleOnStage ? "Finale" : activityLabel}
            </p>
          </div>

          <div className="flex shrink-0 items-center gap-4">
            <Badge tone={paused ? "warning" : "accent"} className="text-base">
              {paused ? "PAUSED" : phase.replaceAll("_", " ").toUpperCase()}
            </Badge>
            <p
              className={[
                "font-mono text-6xl font-extrabold tabular-nums tracking-tight 2xl:text-8xl",
                timeUp ? "text-danger" : "",
              ].join(" ")}
              aria-label="Time left"
            >
              {remainingMs != null ? formatMs(remainingMs) : "--:--"}
            </p>
            <ScreenControls eventId={eventId} />
          </div>
        </header>

        <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] xl:gap-6">
          {/* ── Stage: the most important thing right now ── */}
          <section className="flex flex-col gap-4 xl:gap-6">
            {alibiOnStage ? (
              <>
                <div className="flex-1">
                  <AlibiTvPanel live={alibi} />
                </div>
                {otherFixtures.length > 0 && (
                  <MatchupGrid fixtures={otherFixtures} roster={roster} compact />
                )}
              </>
            ) : playingFixtures.length > 0 ? (
              <>
                <MatchupGrid fixtures={playingFixtures} roster={roster} />
                {restingTeams.length > 0 && (
                  <p className="shrink-0 text-center text-lg text-muted">
                    On a break:{" "}
                    <span className="font-semibold text-foreground">
                      {restingTeams.map((t) => t.name).join(", ")}
                    </span>
                  </p>
                )}
              </>
            ) : finaleOnStage ? (
              <div>
                <RevealBoardView data={reveals} variant="tv" />
              </div>
            ) : (
              <TeamsLobby roster={roster} />
            )}
          </section>

          {/* ── Rail: scores ── */}
          <aside className="flex flex-col gap-4 xl:gap-6">
            <Leaderboard scoreboard={scoreboard} roster={roster} />

            {showClues && releasedClues && <CluesPanel clues={releasedClues} />}

            {/* Fills the rest of the column and scrolls when the list is long. */}
            <StarPlayersCard
              eventId={eventId}
              refreshKey={`${snapshot?.version ?? 0}:${snapshot?.scoreboardVisibility ?? ""}`}
              roster={roster}
              override={preview?.ranking}
            />
          </aside>
        </div>

        {previewState && <PreviewBar eventId={eventId} current={previewState} />}
      </main>
    </RequireAccount>
  );
}

/** Full screen and exit, kept discreet so the room doesn't notice them. */
function ScreenControls({ eventId }: { eventId: string }) {
  function toggleFullScreen() {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      void document.documentElement.requestFullscreen?.();
    }
  }

  return (
    <div className="flex flex-col gap-1 opacity-30 transition-opacity hover:opacity-100 focus-within:opacity-100">
      <button
        type="button"
        onClick={toggleFullScreen}
        className="rounded-control px-2 py-1 text-xs text-muted hover:text-foreground"
        title="Full screen"
      >
        ⛶ Full screen
      </button>
      <Link
        href={`/events/${eventId}`}
        className="rounded-control px-2 py-1 text-xs text-muted hover:text-foreground"
      >
        ✕ Exit
      </Link>
    </div>
  );
}

/** One match fills the stage; two or more share it, still big enough to read across a room. */
function MatchupGrid({
  fixtures,
  roster,
  compact = false,
}: {
  fixtures: ActiveFixtureView[];
  roster: EventRosterResponse | null;
  compact?: boolean;
}) {
  const count = fixtures.length;
  const size: TeamIdentitySize = compact
    ? "sm"
    : count === 1
      ? "xl"
      : count === 2
        ? "lg"
        : "md";

  return (
    <div
      className={[
        "grid min-h-0 gap-4 xl:gap-6",
        compact ? "shrink-0" : "flex-1",
        count === 1 ? "" : count === 2 ? "grid-rows-2" : "md:grid-cols-2",
      ].join(" ")}
    >
      {fixtures.map((f) => (
        <Card
          key={f.fixtureId}
          className={[
            "flex flex-col justify-center gap-4",
            compact ? "px-5 py-3" : "p-6",
          ].join(" ")}
        >
          <div className="flex items-center justify-between gap-3">
            <Badge tone="accent" className={compact ? "" : "text-base"}>
              {f.gameName}
            </Badge>
            <span className={compact ? "text-sm text-muted" : "text-lg text-muted"}>
              Round {f.roundNumber}
              {f.resourceName ? ` · ${f.resourceName}` : ""}
            </span>
          </div>
          <MatchupFaces
            roster={roster}
            teams={f.teams}
            size={size}
            members={compact ? "faces" : "names"}
          />
        </Card>
      ))}
    </div>
  );
}

/** Between games: every team with its players, as big as the screen allows. */
function TeamsLobby({ roster }: { roster: EventRosterResponse | null }) {
  const teams = roster?.teams ?? [];

  if (teams.length === 0) {
    return (
      <Card className="flex flex-1 items-center justify-center p-8">
        <p className="text-3xl text-muted">Teams will appear here.</p>
      </Card>
    );
  }

  const size: TeamIdentitySize = teams.length <= 2 ? "xl" : teams.length <= 4 ? "lg" : "md";

  return (
    <Card className="flex flex-1 flex-col gap-6 p-6 xl:p-8">
      <h2 className="text-3xl font-bold">The teams</h2>
      <div
        className={[
          "grid min-h-0 flex-1 content-center gap-10",
          teams.length <= 2
            ? "grid-cols-2"
            : teams.length <= 4
              ? "grid-cols-2"
              : "grid-cols-3",
        ].join(" ")}
      >
        {teams.map((team) => (
          <TeamIdentity
            key={team.teamId}
            team={team}
            size={size}
            members="names"
            align="center"
          />
        ))}
      </div>
    </Card>
  );
}

function Leaderboard({
  scoreboard,
  roster,
}: {
  scoreboard: ScoreboardResponse | null;
  roster: EventRosterResponse | null;
}) {
  const hidden =
    !scoreboard || scoreboard.hiddenFromViewer || scoreboard.visibility === "hidden";

  if (hidden) {
    return (
      <Card className="shrink-0 space-y-1 p-6">
        <h2 className="text-2xl font-bold">Leaderboard</h2>
        <p className="text-lg text-muted">Scores are hidden for now 🤫</p>
      </Card>
    );
  }

  return (
    <Card className="shrink-0 space-y-4 p-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold 2xl:text-3xl">Leaderboard</h2>
        {scoreboard.visibility === "frozen" && <Badge tone="warning">Frozen</Badge>}
      </div>

      {scoreboard.rows.length === 0 ? (
        <p className="py-6 text-center text-lg text-muted">No scores yet.</p>
      ) : (
        <ol className="space-y-3">
          {scoreboard.rows.map((row) => (
            <li
              key={row.teamId}
              className="flex items-center justify-between gap-3 rounded-[var(--ui-radius-control)] border border-border bg-background/50 px-4 py-3 text-xl 2xl:text-2xl"
            >
              <div className="flex min-w-0 items-center gap-3">
                <span className="w-8 font-mono font-bold text-muted">#{row.rank}</span>
                <RosterAvatar
                  picture={roster?.teams.find((t) => t.teamId === row.teamId)?.picture}
                  label={row.teamName}
                  kind="team"
                  size="md"
                  color={row.teamColor}
                />
                <span className="truncate font-semibold">{row.teamName}</span>
              </div>
              <span className="font-mono font-bold">{row.points}</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}

/** The released clues, compact: one line per team. */
function CluesPanel({ clues }: { clues: ReleasedCluesResponse }) {
  const byTeam = new Map<string, { teamName: string; bodies: string[] }>();
  for (const clue of [...clues.clues].sort((a, b) => a.stage - b.stage)) {
    const entry = byTeam.get(clue.teamId) ?? { teamName: clue.teamName, bodies: [] };
    entry.bodies.push(clue.body);
    byTeam.set(clue.teamId, entry);
  }

  return (
    <Card className="shrink-0 space-y-3 p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-xl font-bold">
          Secret Scene clues · {clues.currentStage}/3
        </h2>
        <Badge tone="accent">
          Guess now: {guessPointsForStage(clues.currentStage)} pts (
          {stageTierLabel(clues.currentStage)})
        </Badge>
      </div>
      <ul className="space-y-2">
        {Array.from(byTeam.entries()).map(([teamId, entry]) => (
          <li key={teamId} className="text-lg">
            <span className="font-semibold">{entry.teamName}:</span>{" "}
            <span className="text-muted">{entry.bodies.join(" · ")}</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** Only in preview mode: switch between sample states. Nothing here touches the event. */
function PreviewBar({ eventId, current }: { eventId: string; current: TvPreviewState }) {
  return (
    <nav
      aria-label="TV preview states"
      className="fixed inset-x-0 bottom-0 z-50 flex flex-wrap items-center justify-center gap-2 border-t border-accent bg-background/95 px-4 py-2 text-sm"
    >
      <Badge tone="warning">PREVIEW · sample data</Badge>
      {TV_PREVIEW_STATES.map((state) => (
        <Link
          key={state.id}
          href={`/events/${eventId}/tv?preview=${state.id}`}
          aria-current={state.id === current ? "page" : undefined}
          className={[
            "rounded-control px-3 py-1",
            state.id === current
              ? "bg-primary text-white"
              : "text-muted hover:bg-surface-raised hover:text-foreground",
          ].join(" ")}
        >
          {state.label}
        </Link>
      ))}
      <Link
        href={`/events/${eventId}/tv`}
        className="rounded-control px-3 py-1 font-semibold text-accent hover:bg-surface-raised"
      >
        Back to live
      </Link>
    </nav>
  );
}
