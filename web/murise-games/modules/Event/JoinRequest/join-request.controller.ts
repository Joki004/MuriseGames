import {
  extractApiErrorMessage,
  isApiResponseError,
} from "@/modules/Api/api.service";
import { JoinRequestService } from "./join-request.service";
import type {
  JoinRequestResponse,
  JoinRequestStatus,
} from "./join-request.type";

export const JoinRequestController = {
  /**
   * Returns null if the current user is not an organizer (403),
   * so the waiting-room panel can stay hidden for regular players.
   */
  async listForOrganizer(
    eventId: string,
    status?: JoinRequestStatus
  ): Promise<JoinRequestResponse[] | null> {
    try {
      const response = await JoinRequestService.list(eventId, status);
      return response.data ?? [];
    } catch (error: unknown) {
      if (isApiResponseError(error) && error.code === 403) {
        return null;
      }
      throw new Error(
        extractApiErrorMessage(error, "Could not load join requests.")
      );
    }
  },

  async approve(
    eventId: string,
    requestId: string,
    teamId: string | null = null
  ): Promise<JoinRequestResponse> {
    try {
      const response = await JoinRequestService.approve(eventId, requestId, {
        teamId,
      });
      if (!response.data) {
        throw new Error("Empty approval response.");
      }
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not approve join request.")
      );
    }
  },

  async reject(
    eventId: string,
    requestId: string
  ): Promise<JoinRequestResponse> {
    try {
      const response = await JoinRequestService.reject(eventId, requestId);
      if (!response.data) {
        throw new Error("Empty rejection response.");
      }
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not reject join request.")
      );
    }
  },
};