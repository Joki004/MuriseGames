import { ApiService } from "@/modules/Api/api.service";
import type { TitleBoardResponse, TitleSaveRequest } from "./title.type";

function eventPath(eventId: string): string {
  return `/api/events/${encodeURIComponent(eventId)}`;
}

export const TitleService = {
  board(eventId: string) {
    return ApiService.get<TitleBoardResponse>(`${eventPath(eventId)}/titles`);
  },

  save(eventId: string, teamId: string, payload: TitleSaveRequest) {
    return ApiService.put<TitleBoardResponse>(
      `${eventPath(eventId)}/teams/${encodeURIComponent(teamId)}/title`,
      payload,
    );
  },
};