import type { AlibiLive } from "@/component/Event/Alibi/Game/AlibiTvCard";
import type { ReleasedCluesResponse } from "@/modules/Event/Clue";
import type { RevealListResponse } from "@/modules/Event/Finale";
import type { ActiveFixtureView } from "@/modules/Event/Live";
import type { StarPlayersRankingResponse } from "@/modules/Event/PlayerPoints";
import {
  NO_PICTURE,
  type EventRosterResponse,
  type RosterTeam,
} from "@/modules/Event/Roster";
import type { ScoreboardResponse } from "@/modules/Event/Scoring";

/**
 * Preview states for the TV screen: `/events/<id>/tv?preview=<state>`.
 * They only change what this browser shows, never the event. The event's real
 * teams and pictures are used; everything else is made-up sample data.
 */
export const TV_PREVIEW_STATES = [
  { id: "lobby", label: "Teams" },
  { id: "match", label: "1 match" },
  { id: "matches", label: "2 matches" },
  { id: "alibi-prep", label: "Alibi prep" },
  { id: "alibi-question", label: "Alibi question" },
  { id: "alibi-reveal", label: "Alibi reveal" },
  { id: "alibi-done", label: "Alibi done" },
  { id: "clues", label: "Clues" },
  { id: "finale", label: "Finale" },
  { id: "hidden", label: "Scores hidden" },
] as const;

export type TvPreviewState = (typeof TV_PREVIEW_STATES)[number]["id"];

export function parseTvPreview(value: string | null): TvPreviewState | null {
  return TV_PREVIEW_STATES.some((s) => s.id === value) ? (value as TvPreviewState) : null;
}

export interface TvPreviewData {
  roster: EventRosterResponse;
  phase: string;
  deadlineAt: number | null;
  fixtures: ActiveFixtureView[];
  alibi: AlibiLive;
  reveals: RevealListResponse | null;
  clues: ReleasedCluesResponse | null;
  scoreboard: ScoreboardResponse;
  ranking: StarPlayersRankingResponse;
}

const SAMPLE_TEAMS: { name: string; color: string; emoji: string; members: string[] }[] = [
  { name: "Red Curtain", color: "#ef4444", emoji: "🎭", members: ["Alex", "Sam", "Maya", "Leo", "Nina"] },
  { name: "Blue Reel", color: "#3b82f6", emoji: "🎬", members: ["Tom", "Eva", "Jo", "Kim"] },
  { name: "Green Room", color: "#22c55e", emoji: "🌙", members: ["Ana", "Ben", "Zoe", "Max", "Ivy"] },
  { name: "Gold Star", color: "#eab308", emoji: "⭐", members: ["Liam", "Mia", "Noah", "Ella"] },
];

const QUESTIONS = [
  "What time did the show start?",
  "Where were you sitting?",
  "What did you do during the interval?",
  "What did you each drink?",
  "What was the play about?",
  "Did either of you go near the backstage door?",
  "Who paid for the tickets?",
  "How did you get home?",
  "What was the weather like?",
  "Who did you meet in the foyer?",
];

/** The real teams when there are at least two, otherwise sample ones. */
function previewRoster(
  real: EventRosterResponse | null,
  eventId: string,
  startedAt: number,
): EventRosterResponse {
  if (real && real.teams.length >= 2) return real;

  const teams: RosterTeam[] = SAMPLE_TEAMS.slice(0, 3).map((t, i) => ({
    teamId: `preview-team-${i}`,
    name: t.name,
    color: t.color,
    sortOrder: i,
    picture: { emoji: t.emoji, imageUrl: null },
    members: t.members.map((name, j) => ({
      participantId: `preview-${i}-${j}`,
      userId: `preview-user-${i}-${j}`,
      displayName: name,
      captain: j === 0,
      picture: NO_PICTURE,
    })),
  }));

  return {
    eventId,
    serverTime: new Date(startedAt).toISOString(),
    imagesExpireAt: new Date(startedAt + 15 * 60_000).toISOString(),
    teams,
    unassignedCount: 0,
  };
}

function fixture(
  id: string,
  gameName: string,
  gameCode: string,
  round: number,
  teams: RosterTeam[],
  resourceName: string | null,
  startedAt: number,
): ActiveFixtureView {
  return {
    fixtureId: `preview-fixture-${id}`,
    eventGameId: `preview-game-${gameCode}`,
    gameName,
    gameCode,
    roundNumber: round,
    status: "active",
    startAt: new Date(startedAt).toISOString(),
    endAt: new Date(startedAt + 20 * 60_000).toISOString(),
    resourceName,
    teams: teams.map((t, i) => ({
      teamId: t.teamId,
      teamName: t.name,
      teamColor: t.color,
      side: i === 0 ? "home" : "away",
    })),
  };
}

export function buildTvPreview(
  state: TvPreviewState,
  realRoster: EventRosterResponse | null,
  eventId: string,
  startedAt: number,
  nowMs: number,
): TvPreviewData {
  const roster = previewRoster(realRoster, eventId, startedAt);
  const [a, b, c] = roster.teams;
  const third = c ?? a;
  const minutes = (n: number) => startedAt + n * 60_000;

  const memberName = (team: RosterTeam, index: number, fallback: string) =>
    team.members[index]?.displayName ?? fallback;

  const grab = fixture("grab", "Grab the Cup", "grab-the-cup", 1, [a, b], "Living room", startedAt);
  const alibiFixture = fixture("alibi", "Unified Alibi", "unified-alibi", 2, [third], "Kitchen", startedAt);

  const fixtures: ActiveFixtureView[] =
    state === "lobby" || state === "finale"
      ? []
      : state === "match" || state === "hidden"
        ? [grab]
        : [grab, alibiFixture];

  const alibiStatus =
    state === "alibi-prep"
      ? "preparing"
      : state === "alibi-question"
        ? "questioning"
        : state === "alibi-reveal"
          ? "judging"
          : state === "alibi-done"
            ? "done"
            : null;

  const revealedCount = alibiStatus === "judging" ? 6 : alibiStatus === "done" ? 10 : 0;
  const revealed = QUESTIONS.slice(0, revealedCount).map((question, i) => ({
    number: i + 1,
    question,
    answerA: ["Half past seven", "Row F", "Ice cream", "Red wine", "A heist"][i % 5],
    answerB: ["7:30", "Row G", "Ice cream", "Water", "A robbery"][i % 5],
    match: [true, false, true, false, true, true, false, true, true, false][i],
  }));

  const alibi: AlibiLive = {
    now: nowMs,
    offsetMs: 0,
    round: alibiStatus
      ? {
          teamName: third.name,
          teamColor: third.color,
          pairNumber: 1,
          playerAName: memberName(third, 0, "Alex"),
          playerBName: memberName(third, 1, "Sam"),
          status: alibiStatus,
          prepEndsAt: new Date(startedAt + 75_000).toISOString(),
          brief:
            alibiStatus === "preparing"
              ? "Last Friday you went to see a play together at the city theatre. Agree on every detail: you will be questioned separately."
              : null,
          currentQuestion: 4,
          questionCount: 10,
          questionText: alibiStatus === "questioning" ? QUESTIONS[3] : null,
          questionClosesAt: new Date(startedAt + 25_000).toISOString(),
          lockedA: true,
          lockedB: false,
          revealed,
          matches: revealed.filter((r) => r.match).length,
        }
      : null,
  };

  const clues: ReleasedCluesResponse | null =
    state === "clues"
      ? {
          eventId,
          currentStage: 2,
          status: "open",
          version: 1,
          releases: [],
          clues: roster.teams.flatMap((t, i) => [
            { teamId: t.teamId, teamName: t.name, teamColor: t.color, stage: 1, body: ["Romance", "A ring", "Rain"][i % 3] },
            { teamId: t.teamId, teamName: t.name, teamColor: t.color, stage: 2, body: ["Verona", "Hobbits", "Singing"][i % 3] },
          ]),
        }
      : null;

  const reveals: RevealListResponse | null =
    state === "finale"
      ? {
          guessingStatus: "closed",
          reveals: roster.teams.map((t, i) => {
            const guesser = roster.teams[(i + 1) % roster.teams.length];
            return {
              sceneId: `preview-scene-${i}`,
              teamId: t.teamId,
              teamName: t.name,
              teamColor: t.color,
              title: ["Romeo and Juliet", "The Lord of the Rings", "Singin' in the Rain"][i % 3],
              answerScope: null,
              revealedAt: new Date(startedAt).toISOString(),
              correctGuessers: [
                { teamId: guesser.teamId, teamName: guesser.name, teamColor: guesser.color, stage: 1, points: 3 },
              ],
              performerBonus: 1,
              leaked: false,
            };
          }),
        }
      : null;

  const points = [17.5, 14, 14, 9.5];
  const scoreboard: ScoreboardResponse = {
    eventId,
    visibility: state === "hidden" ? "hidden" : "public",
    hiddenFromViewer: state === "hidden",
    frozenSnapshotId: null,
    frozenAt: null,
    rows: roster.teams.map((t, i) => ({
      teamId: t.teamId,
      teamName: t.name,
      teamColor: t.color,
      points: points[i % points.length],
      rank: i === 2 ? 2 : i + 1,
    })),
  };

  const players = roster.teams.flatMap((t) =>
    t.members.map((m) => ({ member: m, team: t })),
  );
  const starPoints = [6, 5, 5, 4, 3, 3, 2, 2, 1, 1, 1, 1];
  const rows = players.slice(0, starPoints.length).map((p, i) => ({
    participantId: p.member.participantId,
    userId: p.member.userId,
    displayName: p.member.displayName,
    teamId: p.team.teamId,
    teamName: p.team.name,
    teamColor: p.team.color,
    points: starPoints[i],
    rank: 0,
  }));
  rows.forEach((row, i) => {
    row.rank = i > 0 && rows[i - 1].points === row.points ? rows[i - 1].rank : i + 1;
  });

  return {
    roster,
    phase: state === "lobby" || state === "finale" ? "lobby" : "active_round",
    deadlineAt:
      state === "lobby" || state === "finale"
        ? null
        : state === "alibi-prep"
          ? minutes(19)
          : minutes(12),
    fixtures,
    alibi,
    reveals,
    clues,
    scoreboard,
    ranking: {
      eventId,
      visibility: scoreboard.visibility,
      hiddenFromViewer: scoreboard.hiddenFromViewer,
      frozenAt: null,
      rows,
    },
  };
}
