export interface TeamResponse {
  id: string;
  eventId: string;
  name: string;
  color: string;
  sortOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TeamSaveRequest {
  name: string;
  color: string;
  sortOrder: number;
}

export interface TeamListResponse {
  teams: TeamResponse[];
  canManage: boolean;
}