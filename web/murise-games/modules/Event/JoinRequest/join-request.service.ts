import { ApiService } from "@/modules/Api/api.service";
import type { CustomApiResponse } from "@/modules/Api/api.type";
import type {
  JoinRequestApproveRequest,
  JoinRequestResponse,
  JoinRequestStatus,
} from "./join-request.type";

export const JoinRequestService = {
  list(
    eventId: string,
    status?: JoinRequestStatus
  ): Promise<CustomApiResponse<JoinRequestResponse[]>> {
    const query = status ? `?status=${encodeURIComponent(status)}` : "";
    return ApiService.get<JoinRequestResponse[]>(
      `/api/events/${eventId}/join-requests${query}`
    );
  },

  approve(
    eventId: string,
    requestId: string,
    payload?: JoinRequestApproveRequest
  ): Promise<CustomApiResponse<JoinRequestResponse>> {
    return ApiService.post<JoinRequestResponse>(
      `/api/events/${eventId}/join-requests/${requestId}/approve`,
      payload ?? { teamId: null }
    );
  },

  reject(
    eventId: string,
    requestId: string
  ): Promise<CustomApiResponse<JoinRequestResponse>> {
    return ApiService.post<JoinRequestResponse>(
      `/api/events/${eventId}/join-requests/${requestId}/reject`
    );
  },
};