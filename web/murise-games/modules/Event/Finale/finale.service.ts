import { ApiService } from "@/modules/Api/api.service";
import type {
  LeakRequest,
  ReviewBoardResponse,
  ReviewDecisionRequest,
  RevealListResponse,
  RevealRequest,
  RevealView,
} from "./finale.type";

const base = (eventId: string) =>
  `/api/events/${encodeURIComponent(eventId)}/finale`;

export const FinaleService = {
  getReviews(eventId: string) {
    return ApiService.get<ReviewBoardResponse>(`${base(eventId)}/reviews`);
  },

  decide(eventId: string, guessId: string, payload: ReviewDecisionRequest) {
    return ApiService.put<ReviewBoardResponse>(
      `${base(eventId)}/reviews/${encodeURIComponent(guessId)}`,
      payload,
    );
  },

  recordLeak(eventId: string, payload: LeakRequest) {
    return ApiService.post<ReviewBoardResponse>(
      `${base(eventId)}/leaks`,
      payload,
    );
  },

  getReveals(eventId: string) {
    return ApiService.get<RevealListResponse>(`${base(eventId)}/reveals`);
  },

  reveal(eventId: string, payload: RevealRequest) {
    return ApiService.post<RevealView>(`${base(eventId)}/reveals`, payload);
  },
};
