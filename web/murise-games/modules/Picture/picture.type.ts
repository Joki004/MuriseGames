export type PictureSlot =
  | "AVATAR"
  | "EVENT_ICON"
  | "EVENT_COVER"
  | "TEAM_ICON";

export interface PictureResponse {
  slot: PictureSlot;
  emoji: string | null;
  imageUrl: string | null;
  canEdit: boolean;
}

export const PICTURE_EMOJIS = [
  ["🎲", "Dice"],
  ["🎬", "Movies"],
  ["🎵", "Music"],
  ["🏆", "Trophy"],
  ["🚀", "Rocket"],
  ["🌙", "Moon"],
  ["⭐", "Star"],
  ["🔥", "Fire"],
  ["🌈", "Rainbow"],
  ["🦊", "Fox"],
  ["🐼", "Panda"],
  ["🐉", "Dragon"],
  ["🧩", "Puzzle"],
  ["🎭", "Theatre"],
  ["👑", "Crown"],
  ["💎", "Gem"],
] as const;