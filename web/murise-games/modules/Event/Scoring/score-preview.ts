import {
  ApiService,
  extractApiErrorMessage,
} from "@/modules/Api/api.service";

export type ResultState = "NOT_PLAYED" | "CANCELLED" | "CONFIRMED";

export interface ScorePreviewGame {
  eventGameId: string;
  gameVersionId: string;
  name: string;
  ruleVersion: number;
  headToHead: boolean;
  fields: string[];
  maximumPerField: number;
  inputHint: string;
  decisionNote: string;
  unavailableReason: string | null;
}

export interface PreviewFixtureInput {
  state: ResultState;
  team: number[];
  opponent: number[];
}

export interface ScorePreviewResult {
  gameName: string;
  ruleVersion: number;
  confirmedFixtures: number;
  expectedFixtures: number;
  cancelledFixtures: number;
  rawScore: number;
  rawMaximum: number;
  exactNumerator: string;
  exactDenominator: string;
  displayPoints: string;
  settlementAmount: string;
  complete: boolean;
  message: string;
  fixtures: Array<{
    number: number;
    state: ResultState;
    rawScore: number | null;
    rawMaximum: number;
  }>;
}

export const ScorePreviewApi = {
  async games(eventId: string): Promise<ScorePreviewGame[]> {
    try {
      const response = await ApiService.get<ScorePreviewGame[]>(
        `/api/events/${eventId}/scoring-preview/games`,
      );

      if (!response.data) {
        throw new Error("The scoring options were not returned.");
      }

      return response.data;
    } catch (error) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load scoring options."),
      );
    }
  },

  async calculate(
    eventId: string,
    eventGameId: string,
    fixtures: PreviewFixtureInput[],
  ): Promise<ScorePreviewResult> {
    try {
      const response = await ApiService.post<ScorePreviewResult>(
        `/api/events/${eventId}/scoring-preview`,
        { eventGameId, fixtures },
      );

      if (!response.data) {
        throw new Error("The score preview was not returned.");
      }

      return response.data;
    } catch (error) {
      throw new Error(
        extractApiErrorMessage(error, "Could not calculate this preview."),
      );
    }
  },
};