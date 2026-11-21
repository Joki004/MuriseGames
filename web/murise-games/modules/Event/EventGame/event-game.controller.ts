import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { EventGameService } from "./event-game.service";
import type {
  EventGameAddRequest,
  EventGameResponse,
  EventGameUpdateRequest,
} from "./event-game.type";

export const EventGameController = {
  async list(eventId: string): Promise<EventGameResponse[]> {
    try {
      const response = await EventGameService.list(eventId);
      return response.data ?? [];
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load event lineup.")
      );
    }
  },

  async add(
    eventId: string,
    payload: EventGameAddRequest
  ): Promise<EventGameResponse> {
    try {
      const response = await EventGameService.add(eventId, payload);
      if (!response.data) throw new Error("Empty event game response.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not add game to event.")
      );
    }
  },

  async update(
    eventId: string,
    eventGameId: string,
    payload: EventGameUpdateRequest
  ): Promise<EventGameResponse> {
    try {
      const response = await EventGameService.update(
        eventId,
        eventGameId,
        payload
      );
      if (!response.data) throw new Error("Empty event game response.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not update event game.")
      );
    }
  },
};