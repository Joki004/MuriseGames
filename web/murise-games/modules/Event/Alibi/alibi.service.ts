import { ApiService } from "@/modules/Api/api.service";
import type {
  AlibiAccessRequest,
  AlibiCaseDetail,
  AlibiCaseSaveRequest,
  AlibiPool,
  AlibiPreviewRole,
  AlibiRolePreview,
} from "./alibi.type";

function casesPath(eventId: string): string {
  return `/api/events/${encodeURIComponent(eventId)}/alibi-cases`;
}

function casePath(eventId: string, caseId: string): string {
  return `${casesPath(eventId)}/${encodeURIComponent(caseId)}`;
}

export const AlibiService = {
  list(eventId: string) {
    return ApiService.get<AlibiPool>(casesPath(eventId));
  },

  create(eventId: string, label: string) {
    return ApiService.post<AlibiCaseDetail>(casesPath(eventId), { label });
  },

  get(eventId: string, caseId: string) {
    return ApiService.get<AlibiCaseDetail>(casePath(eventId, caseId));
  },

  save(eventId: string, caseId: string, payload: AlibiCaseSaveRequest) {
    return ApiService.put<AlibiCaseDetail>(casePath(eventId, caseId), payload);
  },

  archive(eventId: string, caseId: string) {
    return ApiService.delete<null>(casePath(eventId, caseId));
  },

  listRequests(eventId: string) {
    return ApiService.get<AlibiAccessRequest[]>(
      `${casesPath(eventId)}/access-requests`,
    );
  },

  requestAccess(eventId: string, caseId: string, reason: string) {
    return ApiService.post<AlibiAccessRequest>(
      `${casePath(eventId, caseId)}/access-requests`,
      { reason },
    );
  },

  decide(eventId: string, requestId: string, approve: boolean, note: string) {
    return ApiService.post<AlibiAccessRequest>(
      `${casesPath(eventId)}/access-requests/${encodeURIComponent(requestId)}/decision`,
      { approve, note },
    );
  },

  preview(
    eventId: string,
    caseId: string,
    role: AlibiPreviewRole,
    version?: number,
  ) {
    const query = new URLSearchParams({ role });
    if (version !== undefined) query.set("version", String(version));
    return ApiService.get<AlibiRolePreview>(
      `${casePath(eventId, caseId)}/preview?${query.toString()}`,
    );
  },
};
