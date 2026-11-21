import { ApiService } from "@/modules/Api/api.service";
import type { CustomApiResponse } from "@/modules/Api/api.type";
import type {
  ScoreEntryCreateRequest,
  ScoreEntryResponse,
  ScoreboardResponse,
  ScoreboardVisibility,
} from "./scoring.type";

export const ScoringService = {
  getScoreboard(eventId: string): Promise<CustomApiResponse<ScoreboardResponse>> {
    return ApiService.get<ScoreboardResponse>(
      `/api/events/${eventId}/scoreboard`
    );
  },

  updateVisibility(
    eventId: string,
    visibility: ScoreboardVisibility
  ): Promise<CustomApiResponse<ScoreboardResponse>> {
    return ApiService.patch<ScoreboardResponse>(
      `/api/events/${eventId}/scoreboard/visibility`,
      { visibility }
    );
  },

  listEntries(eventId: string): Promise<CustomApiResponse<ScoreEntryResponse[]>> {
    return ApiService.get<ScoreEntryResponse[]>(`/api/events/${eventId}/scores`);
  },

  addEntry(
    eventId: string,
    payload: ScoreEntryCreateRequest
  ): Promise<CustomApiResponse<ScoreEntryResponse>> {
    return ApiService.post<ScoreEntryResponse>(
      `/api/events/${eventId}/scores`,
      payload
    );
  },

  reverseEntry(
    eventId: string,
    entryId: string,
    reason: string
  ): Promise<CustomApiResponse<ScoreEntryResponse>> {
    return ApiService.post<ScoreEntryResponse>(
      `/api/events/${eventId}/scores/${entryId}/reverse`,
      { reason }
    );
  },
};