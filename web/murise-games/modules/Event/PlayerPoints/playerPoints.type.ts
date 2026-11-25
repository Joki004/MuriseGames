/** One credit: this player earned this part of the match (Duel 2, Round 1...). */
export interface PlayerCredit {
  segmentIndex: number;
  participantId: string;
}

export interface FixturePlayer {
  participantId: string;
  userId: string | null;
  displayName: string;
}

export interface FixtureTeamPlayers {
  teamId: string;
  teamName: string;
  teamColor: string;
  players: FixturePlayer[];
}

export interface FixtureCreditsResponse {
  fixtureId: string;
  gameName: string;
  roundNumber: number;
  /** Empty when the activity has no star players (e.g. the rehearsal). */
  segments: string[];
  teams: FixtureTeamPlayers[];
  credits: PlayerCredit[];
  unavailableReason: string | null;
}

export interface StarPlayerRow {
  participantId: string;
  userId: string | null;
  displayName: string;
  teamId: string | null;
  teamName: string;
  teamColor: string;
  points: number;
  rank: number;
}

export interface StarPlayersRankingResponse {
  eventId: string;
  visibility: string;
  hiddenFromViewer: boolean;
  frozenAt: string | null;
  rows: StarPlayerRow[];
}
