import { ApiService } from "@/modules/Api/api.service";
import type {
  EventInviteResponse,
  InviteCreatedResponse,
  InviteCreateRequest,
  InviteRedeemResponse,
} from "./invite.type";

function basePath(eventId: string): string {
  return `/api/events/${encodeURIComponent(eventId)}/invites`;
}

export const InviteService = {
  list(eventId: string) {
    return ApiService.get<EventInviteResponse[]>(basePath(eventId));
  },

  create(eventId: string, payload: InviteCreateRequest) {
    return ApiService.post<InviteCreatedResponse>(basePath(eventId), payload);
  },

  redeem(token: string) {
    return ApiService.post<InviteRedeemResponse>("/api/invites/redeem", {
      token,
    });
  },

  revoke(eventId: string, inviteId: string) {
    return ApiService.delete<EventInviteResponse>(
      `${basePath(eventId)}/${encodeURIComponent(inviteId)}`,
    );
  },
};
