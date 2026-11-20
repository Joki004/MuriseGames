import { ApiService } from "@/modules/Api/api.service";
import type {
  TeamListResponse,
  TeamResponse,
  TeamSaveRequest,
} from "./team.type";

function basePath(eventId: string): string {
  return `/api/events/${encodeURIComponent(eventId)}/teams`;
}

export const TeamService = {
  list(eventId: string) {
    return ApiService.get<TeamListResponse>(basePath(eventId));
  },

  create(eventId: string, payload: TeamSaveRequest) {
    return ApiService.post<TeamResponse>(basePath(eventId), payload);
  },

  update(eventId: string, teamId: string, payload: TeamSaveRequest) {
    return ApiService.patch<TeamResponse>(
      `${basePath(eventId)}/${encodeURIComponent(teamId)}`,
      payload,
    );
  },
};