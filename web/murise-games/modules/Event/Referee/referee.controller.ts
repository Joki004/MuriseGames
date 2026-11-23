import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { RefereeService } from "./referee.service";
import type { RefereeBoardResponse, RefereeDeskResponse } from "./referee.type";

function required<T>(data: T | null | undefined, message: string): T {
  if (data == null) throw new Error(message);
  return data;
}

export const RefereeController = {
  async board(eventId: string): Promise<RefereeBoardResponse> {
    try {
      const res = await RefereeService.board(eventId);
      return required(res.data, "Referee assignments were not returned.");
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load referee assignments."),
      );
    }
  },

  async assign(
    eventId: string,
    membershipId: string,
    eventGameId: string,
  ): Promise<RefereeBoardResponse> {
    try {
      const res = await RefereeService.assign(eventId, {
        membershipId,
        eventGameId,
      });
      return required(res.data, "Referee assignments were not returned.");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not assign the referee."));
    }
  },

  async unassign(
    eventId: string,
    assignmentId: string,
  ): Promise<RefereeBoardResponse> {
    try {
      const res = await RefereeService.unassign(eventId, assignmentId);
      return required(res.data, "Referee assignments were not returned.");
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not remove the assignment."),
      );
    }
  },

  async desk(eventId: string): Promise<RefereeDeskResponse> {
    try {
      const res = await RefereeService.desk(eventId);
      return required(res.data, "Referee desk was not returned.");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load your referee desk."));
    }
  },
};
