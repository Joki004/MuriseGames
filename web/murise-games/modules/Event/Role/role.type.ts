export type EventRole = "ORGANIZER" | "REFEREE" | "PLAYER" | "VIEWER";

export interface EventMemberRolesResponse {
  membershipId: string;
  userId: string;
  status: string;
  roles: EventRole[];
}

export interface EventRoleGrantRequest {
  userId: string;
  role: EventRole;
}