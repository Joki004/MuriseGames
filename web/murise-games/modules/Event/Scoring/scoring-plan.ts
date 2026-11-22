import {
  ApiService,
  extractApiErrorMessage,
} from "@/modules/Api/api.service";
import type { ScorePreviewGame } from "./score-preview";

export interface PlanSelection {
  scheduleId: string;
  eventGameIds: string[];
}

export interface PlanGame {
  eventGameId: string;
  gameVersionId: string;
  name: string;
  ruleVersion: number;
  scoringHandler: string;
  adapterRevision: string;
  scoringConfig: Record<string, unknown>;
  rulesMarkdown: string;
  sourceRef: string | null;
  decisionNote: string;
  rawMaximumPerFixture: number;
  fixtures: Array<{
    id: string;
    round: number;
    startsAt: string;
    endsAt: string;
    teamIds: string[];
    bye: boolean;
  }>;
  opportunities: Array<{
    teamId: string;
    appearances: number;
    rawDenominator: number;
    fixtureIds: string[];
  }>;
}

export interface PlanReview {
  eventId: string;
  scheduleId: string;
  scheduleVersion: number;
  policy: "A";
  precision: string;
  reviewHash: string;
  teams: Array<{ id: string; name: string }>;
  games: PlanGame[];
  excludedGames: string[];
  blockers: string[];
  maximumGamePoints: number;
  byeCount: number;
}

export interface PublishedPlan {
  approvedBy: string;
  publishedAt: string;
  decisionNote: string;
  snapshot: PlanReview;
}

export interface PlanSetup {
  drafts: Array<{ id: string; version: number; mode: string }>;
  games: ScorePreviewGame[];
  published: PublishedPlan | null;
}

export interface PublishPlanRequest {
  requestId: string;
  selection: PlanSelection;
  reviewHash: string;
  approvedVersionIds: string[];
  approvePolicy: boolean;
  decisionNote: string;
}

export const ScoringPlanApi = {
  async setup(eventId: string): Promise<PlanSetup> {
    try {
      const response = await ApiService.get<PlanSetup>(
        `/api/events/${eventId}/scoring-plan`,
      );
      if (!response.data) throw new Error("The scoring plan was not returned.");
      return response.data;
    } catch (error) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load the scoring plan."),
      );
    }
  },

  async review(
    eventId: string,
    selection: PlanSelection,
  ): Promise<PlanReview> {
    try {
      const response = await ApiService.post<PlanReview>(
        `/api/events/${eventId}/scoring-plan/review`,
        selection,
      );
      if (!response.data) throw new Error("The review was not returned.");
      return response.data;
    } catch (error) {
      throw new Error(
        extractApiErrorMessage(error, "Could not review this schedule."),
      );
    }
  },

  async publish(
    eventId: string,
    request: PublishPlanRequest,
  ): Promise<PublishedPlan> {
    try {
      const response = await ApiService.post<PublishedPlan>(
        `/api/events/${eventId}/scoring-plan/publish`,
        request,
      );
      if (!response.data) throw new Error("Publication was not confirmed.");
      return response.data;
    } catch (error) {
      throw new Error(
        extractApiErrorMessage(error, "Could not confirm publication."),
      );
    }
  },
};