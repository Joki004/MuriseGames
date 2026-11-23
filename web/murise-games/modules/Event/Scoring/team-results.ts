import { ApiService, extractApiErrorMessage } from "@/modules/Api/api.service";
import type { ScoreboardRowResponse } from "./scoring.type";

/** What team phones show, chosen by the host. */
export type TeamResultsMode = "own_results" | "own_points" | "full_scoreboard";

export interface TeamResultsOpponent {
  teamId: string;
  teamName: string;
  rawScore: number | null;
  values: number[];
  outcome: string | null;
}

export interface TeamResultsMatch {
  fixtureId: string;
  gameName: string;
  roundNumber: number;
  startAt: string;
  /** completed | voided | scheduled | active */
  status: string;
  /** win | loss | draw | placed | dq, once recorded */
  outcome: string | null;
  rawScore: number | null;
  rawMax: number | null;
  fieldLabels: string[];
  values: number[];
  opponents: TeamResultsOpponent[];
  corrected: boolean;
  recordedAt: string | null;
}

export interface TeamResultsGamePoints {
  eventGameId: string;
  gameName: string;
  points: number;
}

export interface MyTeamResultsResponse {
  mode: TeamResultsMode;
  teamId: string | null;
  teamName: string | null;
  teamColor: string | null;
  matches: TeamResultsMatch[];
  /** Null unless the mode is own_points or full_scoreboard. */
  gamePoints: TeamResultsGamePoints[] | null;
  otherPoints: number | null;
  totalPoints: number | null;
  /** Null unless the mode is full_scoreboard. */
  scoreboard: ScoreboardRowResponse[] | null;
}

const base = (eventId: string) =>
  `/api/events/${encodeURIComponent(eventId)}/team-results`;

export const TeamResultsApi = {
  async mine(eventId: string): Promise<MyTeamResultsResponse> {
    try {
      const res = await ApiService.get<MyTeamResultsResponse>(base(eventId));
      if (!res.data) throw new Error("Team results were not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load your results."));
    }
  },

  async getMode(eventId: string): Promise<TeamResultsMode> {
    try {
      const res = await ApiService.get<{ mode: TeamResultsMode }>(
        `${base(eventId)}/settings`,
      );
      if (!res.data) throw new Error("Setting was not returned.");
      return res.data.mode;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load the team results setting."),
      );
    }
  },

  async setMode(eventId: string, mode: TeamResultsMode): Promise<TeamResultsMode> {
    try {
      const res = await ApiService.put<{ mode: TeamResultsMode }>(
        `${base(eventId)}/settings`,
        { mode },
      );
      if (!res.data) throw new Error("Setting was not returned.");
      return res.data.mode;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not save the team results setting."),
      );
    }
  },
};
