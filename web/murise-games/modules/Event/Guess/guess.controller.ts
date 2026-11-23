import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { GuessService } from "./guess.service";
import type {
  CaptainGuessBoardResponse,
  GuessReceipt,
  SubmitGuessRequest,
} from "./guess.type";

export const GuessController = {
  async getBoard(eventId: string): Promise<CaptainGuessBoardResponse> {
    try {
      const res = await GuessService.getBoard(eventId);
      if (!res.data) throw new Error("Guessing board was not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load guessing board."),
      );
    }
  },

  async submit(
    eventId: string,
    payload: SubmitGuessRequest,
  ): Promise<GuessReceipt> {
    try {
      const res = await GuessService.submit(eventId, payload);
      if (!res.data) throw new Error("Guess receipt was not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not submit guess."));
    }
  },
};