import { ApiService } from "@/modules/Api/api.service";
import type {
  AssignRefereeRequest,
  RefereeBoardResponse,
  RefereeDeskResponse,
} from "./referee.type";

const base = (eventId: string) =>
  `/api/events/${encodeURIComponent(eventId)}/referees`;

export const RefereeService = {
  board(eventId: string) {
    return ApiService.get<RefereeBoardResponse>(base(eventId));
  },

  assign(eventId: string, payload: AssignRefereeRequest) {
    return ApiService.post<RefereeBoardResponse>(
      `${base(eventId)}/assignments`,
      payload,
    );
  },

  unassign(eventId: string, assignmentId: string) {
    return ApiService.delete<RefereeBoardResponse>(
      `${base(eventId)}/assignments/${encodeURIComponent(assignmentId)}`,
    );
  },

  desk(eventId: string) {
    return ApiService.get<RefereeDeskResponse>(`${base(eventId)}/me`);
  },
};
