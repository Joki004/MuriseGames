export type ScoreboardVisibility = "public" | "hidden" | "frozen";
export type ScoreCategory =
  | "game_points"
  | "bonus"
  | "penalty"
  | "adjustment"
  | "reversal";

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