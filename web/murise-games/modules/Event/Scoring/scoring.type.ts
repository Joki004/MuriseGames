export type ScoreboardVisibility = "public" | "hidden" | "frozen";
export type ScoreCategory =
  | "game_points"
  | "bonus"
  | "penalty"
  | "adjustment"
  | "reversal"
  | "guess_points"
  | "performer_bonus";

export interface ScoreboardRowResponse {
  teamId: string;
  teamName: string;
  teamColor: string;
  points: number;
  rank: number;
}

export interface ScoreboardResponse {
  eventId: string;
  visibility: ScoreboardVisibility;
  hiddenFromViewer: boolean;
  frozenSnapshotId: string | null;
  frozenAt: string | null;
  rows: ScoreboardRowResponse[];
}

export interface ScoreEntryResponse {
  id: string;
  eventId: string;
  teamId: string;
  teamName: string;
  resultId: string | null;
  category: ScoreCategory;
  delta: number;
  sourceKey: string;
  reason: string;
  actorId: string;
  reversesEntryId: string | null;
  reversed: boolean;
  createdAt: string;
}

export interface ScoreEntryCreateRequest {
  teamId: string;
  category: Exclude<ScoreCategory, "reversal">;
  delta: number;
  reason: string;
  sourceKey?: string;
}

export interface RankedTeam {
  rank: number;
  teamId: string;
  teamName: string;
  exactNumerator: string;
  exactDenominator: string;
  displayPoints: string;
  settledPoints: number;
}

export interface RankingStatusResponse {
  eventId: string;
  policy: string;
  maximumGamePoints: number;
  finalRankingAllowed: boolean;
  blockers: string[];
  includedGames: string[];
  standings: RankedTeam[];
}

export interface RecordFixtureResultRequest {
  teamValues: Record<string, number[]>;
  reason: string;
}

export interface CancelFixtureRequest {
  reason: string;
}

export interface TeamSettlement {
  teamId: string;
  fixtureRawScore: number;
  cumulativeRawScore: number;
  frozenDenominator: number;
  settledTotal: number;
  deltaPosted: number;
  displayTotal: string;
  revision: number;
}

export interface FixtureSettlementResponse {
  eventId: string;
  eventGameId: string;
  fixtureId: string;
  status: string;
  teams: TeamSettlement[];
}