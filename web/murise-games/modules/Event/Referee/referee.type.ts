import type { ScorePreviewGame } from "@/modules/Event/Scoring/score-preview";

export interface RefereeOption {
  membershipId: string;
  displayName: string;
  /** Set when the referee also plays: they can't record their own team's matches. */
  teamId: string | null;
  teamName: string | null;
}

export interface RefereeGameView {
  eventGameId: string;
  name: string;
}

export interface RefereeAssignmentView {
  id: string;
  membershipId: string;
  eventGameId: string;
  createdAt: string;
}

export interface CoverageReferee {
  membershipId: string;
  displayName: string;
  /** Their team plays another match at the same time. */
  busy: boolean;
}

export interface CoverageRow {
  fixtureId: string;
  gameName: string;
  roundNumber: number;
  startAt: string;
  endAt: string;
  teams: DeskFixtureTeam[];
  /** Empty = no assigned referee can record it; only organizers can. */
  referees: CoverageReferee[];
}

export interface RefereeBoardResponse {
  referees: RefereeOption[];
  games: RefereeGameView[];
  assignments: RefereeAssignmentView[];
  /** Empty until the schedule is published. */
  coverage: CoverageRow[];
}

export interface AssignRefereeRequest {
  membershipId: string;
  eventGameId: string;
}

export interface DeskFixtureTeam {
  teamId: string;
  teamName: string;
  side: string;
}

export interface DeskFixture {
  fixtureId: string;
  eventGameId: string;
  gameName: string;
  roundNumber: number;
  status: string;
  startAt: string;
  endAt: string;
  resourceName: string | null;
  teams: DeskFixtureTeam[];
  /** The referee's own team plays: someone else must record it. */
  ownTeam: boolean;
  /** The referee's team plays another match at the same time (warning only). */
  teamBusy: boolean;
}

export interface RefereeDeskResponse {
  games: ScorePreviewGame[];
  fixtures: DeskFixture[];
  schedulePublished: boolean;
}
