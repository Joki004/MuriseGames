export type EventGameStatus = "scheduled" | "active" | "settled" | "voided";

export interface EventGameResponse {
  id: string;
  eventId: string;
  gameDefinitionId: string;
  gameVersionId: string;
  code: string | null;
  name: string;
  kind: string | null;
  summary: string | null;
  participationMode: string | null;
  status: EventGameStatus;
  sortOrder: number;
  houseRulesNote: string | null;
  scoreboardVisibilityOverride: "public" | "hidden" | null;
  settingsSnapshot: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
  rulesVersion: number | null;
  rulesMarkdown: string | null;
  endGameScoringSummary: string | null;
}

export interface EventGameAddRequest {
  gameDefinitionId: string;
  sortOrder?: number;
  houseRulesNote?: string;
}

export interface EventGameUpdateRequest {
  status?: EventGameStatus;
  sortOrder?: number;
  houseRulesNote?: string;
}
