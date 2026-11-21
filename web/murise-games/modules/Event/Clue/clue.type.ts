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