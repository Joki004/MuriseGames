import { ApiService } from "@/modules/Api/api.service";
import type {
  AddFixtureRequest,
  CreateResourceRequest,
  GenerateBalancedDraftRequest,
  ResourceView,
  ScheduleDetail,
  ScheduleSummary,
} from "./schedule.type";

const base = (eventId: string) =>
  `/api/v1/events/${encodeURIComponent(eventId)}`;

export const ScheduleService = {
  listSchedules(eventId: string) {
    return ApiService.get<ScheduleSummary[]>(`${base(eventId)}/schedules`);
  },

  getSchedule(eventId: string, scheduleId: string) {
    return ApiService.get<ScheduleDetail>(
      `${base(eventId)}/schedules/${encodeURIComponent(scheduleId)}`,
    );
  },

  createManualDraft(eventId: string) {
    return ApiService.post<ScheduleDetail>(`${base(eventId)}/schedules/manual`, {});
  },

  generateBalancedDraft(
    eventId: string,
    payload: GenerateBalancedDraftRequest,
  ) {
    return ApiService.post<ScheduleDetail>(
      `${base(eventId)}/schedules/balanced`,
      payload,
    );
  },

  addFixture(
    eventId: string,
    scheduleId: string,
    payload: AddFixtureRequest,
  ) {
    return ApiService.post<ScheduleDetail>(
      `${base(eventId)}/schedules/${encodeURIComponent(scheduleId)}/fixtures`,
      payload,
    );
  },

  deleteFixture(eventId: string, scheduleId: string, fixtureId: string) {
    return ApiService.delete<ScheduleDetail>(
      `${base(eventId)}/schedules/${encodeURIComponent(scheduleId)}/fixtures/${encodeURIComponent(fixtureId)}`,
    );
  },

  listResources(eventId: string) {
    return ApiService.get<ResourceView[]>(`${base(eventId)}/resources`);
  },

  createResource(eventId: string, payload: CreateResourceRequest) {
    return ApiService.post<ResourceView>(`${base(eventId)}/resources`, payload);
  },
};