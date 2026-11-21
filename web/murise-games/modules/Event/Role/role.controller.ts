import { EventRoleService } from "./role.service";
import type { EventRole } from "./role.type";

export const EventRoleController = {
  async list(eventId: string) {
    const res = await EventRoleService.list(eventId);
    return res.data ?? [];
  },

  async grant(eventId: string, userId: string, role: EventRole) {
    const res = await EventRoleService.grant(eventId, { userId, role });
    if (!res.data) {
      throw new Error("Empty response while granting event role.");
    }
    return res.data;
  },

  async revoke(eventId: string, userId: string, role: EventRole) {
    const res = await EventRoleService.revoke(eventId, userId, role);
    if (!res.data) {
      throw new Error("Empty response while revoking event role.");
    }
    return res.data;
  },
};