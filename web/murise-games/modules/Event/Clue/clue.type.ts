export interface ClueResponse {
  stage: number;
  body: string;
  approved: boolean;
}

export interface TeamClueSetResponse {
  teamId: string;
  teamName: string;
  teamColor: string;
  clues: ClueResponse[];
}

export interface StageReadinessResponse {
  stage: number;
  approvedTeams: number;
  totalTeams: number;
  canRelease: boolean;
}

export interface ClueBoardResponse {
  teams: TeamClueSetResponse[];
  stages: StageReadinessResponse[];
}

export interface ClueSaveItem {
  body: string;
  approved: boolean;
}

export interface ClueSetSaveRequest {
  clues: ClueSaveItem[];
}

export interface StageCommandRequest {
  requestId: string;
  expectedStage: number;
  expectedVersion: number;
}

export interface PublicClue {
  teamId: string;
  teamName: string;
  teamColor: string;
  stage: number;
  body: string;
}

export interface ReleaseRecord {
  stage: number;
  releasedBy: string;
  releasedAt: string;
}

export interface ReleasedCluesResponse {
  eventId: string;
  currentStage: number;
  status: "open" | "closed" | string;
  version: number;
  releases: ReleaseRecord[];
  clues: PublicClue[];
}