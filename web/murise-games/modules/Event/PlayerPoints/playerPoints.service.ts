import { ApiService } from "@/modules/Api/api.service";
import type {
  FixtureCreditsResponse,
  PlayerCredit,
  StarPlayersRankingResponse,
} from "./playerPoints.type";

function eventPath(eventId: string) {
  return `/api/events/${encodeURIComponent(eventId)}`;
}

function fixturePath(eventId: string, fixtureId: string) {
  return `${eventPath(eventId)}/fixtures/${encodeURIComponent(fixtureId)}/player-points`;
}

export const PlayerPointsService = {
  fixture(eventId: string, fixtureId: string) {
    return ApiService.get<FixtureCreditsResponse>(fixturePath(eventId, fixtureId));
  },

  save(eventId: string, fixtureId: string, credits: PlayerCredit[]) {
    return ApiService.put<FixtureCreditsResponse>(fixturePath(eventId, fixtureId), {
      credits,
    });
  },

  ranking(eventId: string) {
    return ApiService.get<StarPlayersRankingResponse>(
      `${eventPath(eventId)}/player-points/ranking`,
    );
  },
};
