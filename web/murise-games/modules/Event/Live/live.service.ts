import { ApiService } from "@/modules/Api/api.service";
import type {
  EventSnapshotResponse,
  EventStateUpdateRequest,
} from "./live.type";

export const EventLiveService = {
  snapshot(eventId: string) {
    return ApiService.get<EventSnapshotResponse>(
      `/api/v1/events/${encodeURIComponent(eventId)}/snapshot`
    );
  },

  updateState(eventId: string, body: EventStateUpdateRequest) {
    return ApiService.put<EventSnapshotResponse>(
      `/api/v1/events/${encodeURIComponent(eventId)}/state`,
      body
    );
  },

  pause(eventId: string) {
    return ApiService.post<EventSnapshotResponse>(
      `/api/v1/events/${encodeURIComponent(eventId)}/state/pause`
    );
  },

  resume(eventId: string) {
    return ApiService.post<EventSnapshotResponse>(
      `/api/v1/events/${encodeURIComponent(eventId)}/state/resume`
    );
  },

  setReady(eventId: string, teamId: string, ready: boolean) {
    return ApiService.post<EventSnapshotResponse>(
      `/api/v1/events/${encodeURIComponent(eventId)}/state/ready`,
      { teamId, ready }
    );
  },

  reset(eventId: string) {
    return ApiService.post<EventSnapshotResponse>(
      `/api/v1/events/${encodeURIComponent(eventId)}/state/reset`
    );
  },
};