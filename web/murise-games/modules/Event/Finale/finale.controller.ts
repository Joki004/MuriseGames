import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { FinaleService } from "./finale.service";
import type {
  ReviewBoardResponse,
  ReviewVerdict,
  RevealListResponse,
  RevealRequest,
  RevealView,
} from "./finale.type";

export const FinaleController = {
  async getReviews(eventId: string): Promise<ReviewBoardResponse> {
    try {
      const res = await FinaleService.getReviews(eventId);
      if (!res.data) throw new Error("Guess reviews were not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load guess reviews."),
      );
    }
  },

  async decide(
    eventId: string,
    guessId: string,
    verdict: ReviewVerdict,
  ): Promise<ReviewBoardResponse> {
    try {
      const res = await FinaleService.decide(eventId, guessId, { verdict });
      if (!res.data) throw new Error("Guess reviews were not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not save the guess review."),
      );
    }
  },

  async recordLeak(
    eventId: string,
    teamId: string,
  ): Promise<ReviewBoardResponse> {
    try {
      const res = await FinaleService.recordLeak(eventId, { teamId });
      if (!res.data) throw new Error("Guess reviews were not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not record the leak."));
    }
  },

  async getReveals(eventId: string): Promise<RevealListResponse> {
    try {
      const res = await FinaleService.getReveals(eventId);
      if (!res.data) throw new Error("Reveals were not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load reveals."));
    }
  },

  async reveal(eventId: string, payload: RevealRequest): Promise<RevealView> {
    try {
      const res = await FinaleService.reveal(eventId, payload);
      if (!res.data) throw new Error("Reveal result was not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not reveal this title."),
      );
    }
  },
};
