import { ApiService } from "@/modules/Api/api.service";
import type { ClueBoardResponse, ClueSetSaveRequest, ReleasedCluesResponse, StageCommandRequest } from "./clue.type";

function eventPath(eventId: string): string {
  return `/api/events/${encodeURIComponent(eventId)}`;
}

export const ClueService = {
  board(eventId: string) {
    return ApiService.get<ClueBoardResponse>(`${eventPath(eventId)}/clues`);
  },

  save(eventId: string, teamId: string, payload: ClueSetSaveRequest) {
    return ApiService.put<ClueBoardResponse>(
      `${eventPath(eventId)}/teams/${encodeURIComponent(teamId)}/clues`,
      payload,
    );
  },

  getReleased(eventId: string) {
    return ApiService.get<ReleasedCluesResponse>(
      `/api/events/${encodeURIComponent(eventId)}/clues/released`,
    );
  },

  releaseNext(eventId: string, payload: StageCommandRequest) {
    return ApiService.post<ReleasedCluesResponse>(
      `/api/events/${encodeURIComponent(eventId)}/clues/release`,
      payload,
    );
  },

  closeGuessing(eventId: string, payload: StageCommandRequest) {
    return ApiService.post<ReleasedCluesResponse>(
      `/api/events/${encodeURIComponent(eventId)}/clues/close-guessing`,
      payload,
    );
  },
};