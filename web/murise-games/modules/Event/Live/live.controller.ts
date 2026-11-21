import { EventLiveService } from "./live.service";
import type { EventStateUpdateRequest } from "./live.type";

export const EventLiveController = {
  async snapshot(eventId: string) {
    const res = await EventLiveService.snapshot(eventId);
    if (!res.data) throw new Error("Empty snapshot response.");
    return res.data;
  },

  async updateState(eventId: string, body: EventStateUpdateRequest) {
    const res = await EventLiveService.updateState(eventId, body);
    if (!res.data) throw new Error("Empty state update response.");
    return res.data;
  },

  async pause(eventId: string) {
    const res = await EventLiveService.pause(eventId);
    if (!res.data) throw new Error("Empty pause response.");
    return res.data;
  },

  async resume(eventId: string) {
    const res = await EventLiveService.resume(eventId);
    if (!res.data) throw new Error("Empty resume response.");
    return res.data;
  },
};