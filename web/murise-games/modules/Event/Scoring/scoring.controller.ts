import {
  extractApiErrorMessage,
  isApiResponseError,
} from "@/modules/Api/api.service";
import { ScoringService } from "./scoring.service";
import type {
  CancelFixtureRequest,
  FixtureSettlementResponse,
  RankingStatusResponse,
  RecordFixtureResultRequest,
  ScoreEntryCreateRequest,
  ScoreEntryResponse,
  ScoreboardResponse,
  ScoreboardVisibility,
} from "./scoring.type";

export const ScoringController = {
  async getScoreboard(eventId: string): Promise<ScoreboardResponse> {
    try {
      const response = await ScoringService.getScoreboard(eventId);
      if (!response.data) throw new Error("Empty scoreboard response.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load scoreboard.")
      );
    }
  },

  async updateVisibility(
    eventId: string,
    visibility: ScoreboardVisibility
  ): Promise<ScoreboardResponse> {
    try {
      const response = await ScoringService.updateVisibility(eventId, visibility);
      if (!response.data) throw new Error("Empty scoreboard response.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not update scoreboard visibility.")
      );
    }
  },

  /** Returns null for non-organizers (403) so ledger controls stay hidden. */
  async listEntriesForOrganizer(
    eventId: string
  ): Promise<ScoreEntryResponse[] | null> {
    try {
      const response = await ScoringService.listEntries(eventId);
      return response.data ?? [];
    } catch (error: unknown) {
      if (isApiResponseError(error) && error.code === 403) {
        return null;
      }
      throw new Error(
        extractApiErrorMessage(error, "Could not load score ledger.")
      );
    }
  },

  async addEntry(
    eventId: string,
    payload: ScoreEntryCreateRequest
  ): Promise<ScoreEntryResponse> {
    try {
      const response = await ScoringService.addEntry(eventId, payload);
      if (!response.data) throw new Error("Empty score entry response.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not record score entry.")
      );
    }
  },

  async reverseEntry(
    eventId: string,
    entryId: string,
    reason: string
  ): Promise<ScoreEntryResponse> {
    try {
      const response = await ScoringService.reverseEntry(
        eventId,
        entryId,
        reason
      );
      if (!response.data) throw new Error("Empty reversal response.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not reverse score entry.")
      );
    }
  },

  async getRankingStatus(eventId: string): Promise<RankingStatusResponse> {
    try {
      const response = await ScoringService.getRankingStatus(eventId);
      if (!response.data) {
        throw new Error("Ranking status payload was empty.");
      }
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load ranking status."),
      );
    }
  },

  async recordFixtureResult(
    eventId: string,
    fixtureId: string,
    payload: RecordFixtureResultRequest,
  ): Promise<FixtureSettlementResponse | null> {
    try {
      const response = await ScoringService.recordFixtureResult(
        eventId,
        fixtureId,
        payload,
      );
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not record fixture result."),
      );
    }
  },

  async cancelFixture(
    eventId: string,
    fixtureId: string,
    payload: CancelFixtureRequest,
  ): Promise<void> {
    try {
      await ScoringService.cancelFixture(eventId, fixtureId, payload);
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not cancel fixture."),
      );
    }
  },
};