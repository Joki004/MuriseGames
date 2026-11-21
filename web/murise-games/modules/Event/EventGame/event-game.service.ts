import { ApiService } from "@/modules/Api/api.service";
import type { CustomApiResponse } from "@/modules/Api/api.type";
import type {
  EventGameAddRequest,
  EventGameResponse,
  EventGameUpdateRequest,
} from "./event-game.type";

export const EventGameService = {
  list(eventId: string): Promise<CustomApiResponse<EventGameResponse[]>> {
    return ApiService.get<EventGameResponse[]>(`/api/events/${eventId}/games`);
  },

  add(
    eventId: string,
    payload: EventGameAddRequest
  ): Promise<CustomApiResponse<EventGameResponse>> {
    return ApiService.post<EventGameResponse>(
      `/api/events/${eventId}/games`,
      payload
    );
  },

  update(
    eventId: string,
    eventGameId: string,
    payload: EventGameUpdateRequest
  ): Promise<CustomApiResponse<EventGameResponse>> {
    return ApiService.patch<EventGameResponse>(
      `/api/events/${eventId}/games/${eventGameId}`,
      payload
    );
  },
};