import { ApiService } from "@/modules/Api/api.service";
import type { CustomApiResponse } from "@/modules/Api/api.type";
import type {
  EventParticipantResponse,
  ParticipantTeamAssignRequest,
  TeamOption,
} from "./participant.type";

type RawTeamsPayload =
  | TeamOption[]
  | { teams?: TeamOption[]; content?: TeamOption[] };

export const ParticipantService = {
  list(eventId: string): Promise<CustomApiResponse<EventParticipantResponse[]>> {
    return ApiService.get<EventParticipantResponse[]>(
      `/api/events/${eventId}/participants`
    );
  },

  assignTeam(
    eventId: string,
    participantId: string,
    payload: ParticipantTeamAssignRequest
  ): Promise<CustomApiResponse<EventParticipantResponse>> {
    return ApiService.patch<EventParticipantResponse>(
      `/api/events/${eventId}/participants/${participantId}/team`,
      payload
    );
  },

  async listTeamOptions(eventId: string): Promise<TeamOption[]> {
    const response = await ApiService.get<RawTeamsPayload>(
      `/api/events/${eventId}/teams`
    );
    const data = response.data;
    if (!data) return [];
    if (Array.isArray(data)) return data;
    return data.teams ?? data.content ?? [];
  },

  setCaptain(eventId: string, participantId: string, captain: boolean) {
    return ApiService.patch<EventParticipantResponse>(
      `/api/events/${encodeURIComponent(eventId)}/participants/${encodeURIComponent(participantId)}/captain`,
      { captain },
    );
  },
};