export type EventRole =
  | "ORGANIZER"
  | "REFEREE"
  | "PLAYER"
  | "VIEWER"
  | "CASE_WRITER";

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