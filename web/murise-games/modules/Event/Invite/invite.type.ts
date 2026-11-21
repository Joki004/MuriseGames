export interface EventInviteResponse {
  id: string;
  eventId: string;
  expiresAt: string;
  revokedAt: string | null;
  maxUses: number | null;
  useCount: number;
  createdAt: string;
  status: "ACTIVE" | "REVOKED" | "EXPIRED" | "EXHAUSTED";
}

export interface InviteCreateRequest {
  expiresInHours: number;
  maxUses: number | null;
}

export interface InviteCreatedResponse {
  invite: EventInviteResponse;
  token: string;
}

export type InviteRedeemResponse =
  | {
      status: "JOINED";
      eventId: string;
      requestId: null;
    }
  | {
      status: "PENDING";
      eventId: null;
      requestId: string;
    };