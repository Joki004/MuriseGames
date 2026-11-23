import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { ScheduleService } from "./schedule.service";
import type {
  AddFixtureRequest,
  CreateResourceRequest,
  GenerateBalancedDraftRequest,
  ResourceView,
  ScheduleDetail,
  ScheduleSummary,
} from "./schedule.type";

export const ScheduleController = {
  async listSchedules(eventId: string): Promise<ScheduleSummary[]> {
    try {
      const res = await ScheduleService.listSchedules(eventId);
      return res.data ?? [];
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load schedules."),
      );
    }
  },

  async getSchedule(
    eventId: string,
    scheduleId: string,
  ): Promise<ScheduleDetail> {
    try {
      const res = await ScheduleService.getSchedule(eventId, scheduleId);
      if (!res.data) throw new Error("Schedule detail was not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load schedule details."),
      );
    }
  },

  async createManualDraft(eventId: string): Promise<ScheduleDetail> {
    try {
      const res = await ScheduleService.createManualDraft(eventId);
      if (!res.data) throw new Error("Draft schedule was not created.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not create manual draft."),
      );
    }
  },

  async generateBalancedDraft(
    eventId: string,
    payload: GenerateBalancedDraftRequest,
  ): Promise<ScheduleDetail> {
    try {
      const res = await ScheduleService.generateBalancedDraft(eventId, payload);
      if (!res.data) throw new Error("Balanced schedule was not generated.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not generate balanced schedule."),
      );
    }
  },

  async addFixture(
    eventId: string,
    scheduleId: string,
    payload: AddFixtureRequest,
  ): Promise<ScheduleDetail> {
    try {
      const res = await ScheduleService.addFixture(
        eventId,
        scheduleId,
        payload,
      );
      if (!res.data) throw new Error("Fixture was not added.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not add fixture."));
    }
  },

  async deleteFixture(
    eventId: string,
    scheduleId: string,
    fixtureId: string,
  ): Promise<ScheduleDetail> {
    try {
      const res = await ScheduleService.deleteFixture(
        eventId,
        scheduleId,
        fixtureId,
      );
      if (!res.data) throw new Error("Fixture was not removed.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not remove fixture."),
      );
    }
  },

  async listResources(eventId: string): Promise<ResourceView[]> {
    try {
      const res = await ScheduleService.listResources(eventId);
      return res.data ?? [];
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load event resources."),
      );
    }
  },

  async createResource(
    eventId: string,
    payload: CreateResourceRequest,
  ): Promise<ResourceView> {
    try {
      const res = await ScheduleService.createResource(eventId, payload);
      if (!res.data) throw new Error("Resource was not created.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not create resource."),
      );
    }
  },
};