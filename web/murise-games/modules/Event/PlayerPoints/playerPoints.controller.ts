import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { PlayerPointsService } from "./playerPoints.service";
import type {
  FixtureCreditsResponse,
  PlayerCredit,
  StarPlayersRankingResponse,
} from "./playerPoints.type";

function unwrap<T>(data: T | null | undefined, what: string): T {
  if (!data) throw new Error(`${what} payload was empty.`);
  return data;
}

export const PlayerPointsController = {
  async fixture(eventId: string, fixtureId: string): Promise<FixtureCreditsResponse> {
    try {
      const response = await PlayerPointsService.fixture(eventId, fixtureId);
      return unwrap(response.data, "Match players");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load the players."));
    }
  },

  async save(
    eventId: string,
    fixtureId: string,
    credits: PlayerCredit[],
  ): Promise<FixtureCreditsResponse> {
    try {
      const response = await PlayerPointsService.save(eventId, fixtureId, credits);
      return unwrap(response.data, "Star players");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not save the star players."));
    }
  },

  async ranking(eventId: string): Promise<StarPlayersRankingResponse> {
    try {
      const response = await PlayerPointsService.ranking(eventId);
      return unwrap(response.data, "Star players");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load the star players."));
    }
  },
};

export function creditKey(credit: PlayerCredit) {
  return `${credit.segmentIndex}:${credit.participantId}`;
}
