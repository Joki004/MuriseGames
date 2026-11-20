import { ApiService } from "@/modules/Api/api.service";
import type { CustomApiResponse } from "@/modules/Api/api.type";
import type {
  EventCreateRequest,
  EventPage,
  EventResponse,
} from "./event.type";

export const EventService = {
  create(
    payload: EventCreateRequest,
  ): Promise<CustomApiResponse<EventResponse>> {
    return ApiService.post<EventResponse>("/api/events", payload);
  },

  listMine(
    page = 0,
    size = 10,
  ): Promise<CustomApiResponse<EventPage>> {
    const query = new URLSearchParams({
      page: String(page),
      size: String(size),
    });

    return ApiService.get<EventPage>(`/api/events?${query}`);
  },
};