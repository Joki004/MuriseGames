import { ApiService } from "@/modules/Api/api.service";
import type {
  CaptainGuessBoardResponse,
  GuessReceipt,
  SubmitGuessRequest,
} from "./guess.type";

const base = (eventId: string) =>
  `/api/events/${encodeURIComponent(eventId)}/guesses`;

export const GuessService = {
  getBoard(eventId: string) {
    return ApiService.get<CaptainGuessBoardResponse>(base(eventId));
  },

  submit(eventId: string, payload: SubmitGuessRequest) {
    return ApiService.post<GuessReceipt>(base(eventId), payload);
  },
};