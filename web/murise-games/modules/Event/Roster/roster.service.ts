import { ApiService } from "@/modules/Api/api.service";
import type { EventRosterResponse } from "./roster.type";

export const RosterService = {
  get(eventId: string) {
    return ApiService.get<EventRosterResponse>(
      `/api/events/${encodeURIComponent(eventId)}/roster`,
    );
  },
};
