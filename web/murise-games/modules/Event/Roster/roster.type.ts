/** emoji and imageUrl may both be null: show initials then. */
export interface RosterPicture {
  emoji: string | null;
  imageUrl: string | null;
}

export interface RosterMember {
  participantId: string;
  userId: string | null;
  displayName: string;
  captain: boolean;
  picture: RosterPicture;
}

export interface RosterTeam {
  teamId: string;
  name: string;
  color: string;
  sortOrder: number;
  picture: RosterPicture;
  members: RosterMember[];
}

export interface EventRosterResponse {
  eventId: string;
  serverTime: string;
  /** Signed image URLs stop working after this; the hook refreshes before. */
  imagesExpireAt: string;
  teams: RosterTeam[];
  unassignedCount: number;
}

export const NO_PICTURE: RosterPicture = { emoji: null, imageUrl: null };
