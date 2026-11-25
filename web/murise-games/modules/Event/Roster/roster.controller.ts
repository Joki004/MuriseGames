import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { RosterService } from "./roster.service";
import {
  NO_PICTURE,
  type EventRosterResponse,
  type RosterTeam,
} from "./roster.type";

export const RosterController = {
  async get(eventId: string): Promise<EventRosterResponse> {
    try {
      const response = await RosterService.get(eventId);
      if (!response.data) throw new Error("Roster payload was empty.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load the teams."));
    }
  },
};

/**
 * The roster team for a fixture team. Falls back to the name and color the
 * live snapshot already has, so screens work before the roster has loaded.
 */
export function rosterTeamFor(
  roster: EventRosterResponse | null,
  teamId: string,
  fallbackName: string,
  fallbackColor: string,
): RosterTeam {
  return (
    roster?.teams.find((t) => t.teamId === teamId) ?? {
      teamId,
      name: fallbackName,
      color: fallbackColor,
      sortOrder: 0,
      picture: NO_PICTURE,
      members: [],
    }
  );
}
