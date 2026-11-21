import { ApiService } from "@/modules/Api/api.service";
import type {
  EventMemberRolesResponse,
  EventRole,
  EventRoleGrantRequest,
} from "./role.type";

export const EventRoleService = {
  list(eventId: string) {
    return ApiService.get<EventMemberRolesResponse[]>(
      `/api/v1/events/${encodeURIComponent(eventId)}/roles`
    );
  },

  grant(eventId: string, body: EventRoleGrantRequest) {
    return ApiService.post<EventMemberRolesResponse>(
      `/api/v1/events/${encodeURIComponent(eventId)}/roles`,
      body
    );
  },

  revoke(eventId: string, userId: string, role: EventRole) {
    return ApiService.delete<EventMemberRolesResponse>(
      `/api/v1/events/${encodeURIComponent(eventId)}/roles/${encodeURIComponent(userId)}/${encodeURIComponent(role)}`
    );
  },
};