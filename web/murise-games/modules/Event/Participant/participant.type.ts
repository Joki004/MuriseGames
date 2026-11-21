export interface EventParticipantResponse {
  id: string;
  eventId: string;
  userId: string | null;
  displayName: string;
  teamId: string | null;
  status: string;
  captain: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ParticipantTeamAssignRequest {
  teamId: string | null;
}

export interface TeamOption {
  id: string;
  name: string;
  active?: boolean;
}