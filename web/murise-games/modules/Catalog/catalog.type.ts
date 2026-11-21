export type GameKind =
  | "competitive"
  | "party"
  | "social_deduction"
  | "rehearsal";

export type ParticipationMode =
  | "head_to_head"
  | "team_solo"
  | "all_teams"
  | "multi_team_pod"
  | "individual";

export interface CatalogGameResponse {
  definitionId: string;
  latestVersionId: string;
  version: number;
  code: string;
  name: string;
  kind: GameKind;
  summary: string;
  rulesMarkdown: string;
  endGameScoringSummary: string;
  participationMode: ParticipationMode;
  scoringHandler: string;
  scoringConfig: Record<string, unknown>;
}

export interface CatalogGameCreateRequest {
  code: string;
  name: string;
  kind: GameKind;
  summary: string;
  rulesMarkdown: string;
  endGameScoringSummary: string;
  participationMode: ParticipationMode;
  scoringHandler: string;
  scoringConfig?: Record<string, unknown>;
}