import { ApiService } from "@/modules/Api/api.service";
import type {
  SocialAccessRequestView,
  SocialCaseContent,
  SocialCaseDetail,
  SocialPoolResponse,
  SocialRoleAssignmentRequest,
  SocialRoleAssignmentView,
  SocialRoleCardView,
  SocialVersionDetail,
} from "./social.type";

function casesPath(eventId: string): string {
  return `/api/events/${encodeURIComponent(eventId)}/social-cases`;
}

function casePath(eventId: string, caseId: string): string {
  return `${casesPath(eventId)}/${encodeURIComponent(caseId)}`;
}

export const SocialCaseService = {
  list(eventId: string) {
    return ApiService.get<SocialPoolResponse>(casesPath(eventId));
  },

  create(eventId: string, label: string) {
    return ApiService.post<SocialCaseDetail>(casesPath(eventId), { label });
  },

  get(eventId: string, caseId: string) {
    return ApiService.get<SocialCaseDetail>(casePath(eventId, caseId));
  },

  save(
    eventId: string,
    caseId: string,
    payload: { label: string; baseVersion: number; content: SocialCaseContent },
  ) {
    return ApiService.put<SocialCaseDetail>(casePath(eventId, caseId), payload);
  },

  archive(eventId: string, caseId: string) {
    return ApiService.delete<null>(casePath(eventId, caseId));
  },

  version(eventId: string, caseId: string, versionNumber: number) {
    return ApiService.get<SocialVersionDetail>(
      `${casePath(eventId, caseId)}/versions/${encodeURIComponent(String(versionNumber))}`,
    );
  },

  listRequests(eventId: string) {
    return ApiService.get<SocialAccessRequestView[]>(
      `${casesPath(eventId)}/access-requests`,
    );
  },

  requestAccess(eventId: string, caseId: string, reason: string) {
    return ApiService.post<SocialAccessRequestView>(
      `${casePath(eventId, caseId)}/access-requests`,
      { reason },
    );
  },

  decide(eventId: string, requestId: string, approve: boolean, note = "") {
    return ApiService.post<SocialAccessRequestView>(
      `${casesPath(eventId)}/access-requests/${encodeURIComponent(requestId)}/decision`,
      { approve, note },
    );
  },

  assignRole(eventId: string, payload: SocialRoleAssignmentRequest) {
    return ApiService.post<SocialRoleAssignmentView>(
      `${casesPath(eventId)}/role-assignments`,
      payload,
    );
  },

  myRoleCard(eventId: string, roundNumber = 1) {
    const query = new URLSearchParams({ roundNumber: String(roundNumber) });
    return ApiService.get<SocialRoleCardView>(
      `${casesPath(eventId)}/role-card?${query.toString()}`,
    );
  },
};