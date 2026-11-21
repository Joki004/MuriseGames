import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { ParticipantService } from "./participant.service";
import type { EventParticipantResponse, TeamOption } from "./participant.type";

export const ParticipantController = {
  async list(eventId: string): Promise<EventParticipantResponse[]> {
    try {
      const response = await ParticipantService.list(eventId);
      return response.data ?? [];
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load participants.")
      );
    }
  },

  async listActiveTeamOptions(eventId: string): Promise<TeamOption[]> {
    try {
      const teams = await ParticipantService.listTeamOptions(eventId);
      return teams.filter((team) => team.active !== false);
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load teams."));
    }
  },

  async assignTeam(
    eventId: string,
    participantId: string,
    teamId: string | null
  ): Promise<EventParticipantResponse> {
    try {
      const response = await ParticipantService.assignTeam(
        eventId,
        participantId,
        { teamId }
      );
      if (!response.data) {
        throw new Error("Empty participant response.");
      }
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not update participant team.")
      );
    }
  },
};