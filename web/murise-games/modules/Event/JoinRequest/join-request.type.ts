export type JoinRequestStatus = "pending" | "approved" | "rejected";

export interface JoinRequestResponse {
  id: string;
  eventId: string;
  userId: string;
  displayName: string;
  inviteId: string | null;
  status: JoinRequestStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface JoinRequestApproveRequest {
  teamId: string | null;
}