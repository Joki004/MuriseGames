import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { AlibiService } from "./alibi.service";
import type {
  AlibiAccessRequest,
  AlibiCaseContent,
  AlibiCaseDetail,
  AlibiPool,
  AlibiPreviewRole,
  AlibiRolePreview,
} from "./alibi.type";

export const ALIBI_QUESTION_COUNT = 10;
export const ALIBI_LABEL_MAX = 80;
export const ALIBI_MAX_FIXED_FACTS = 12;
export const ALIBI_REASON_MAX = 500;

/** One entry per line; blank lines are ignored. */
export function splitLines(text: string): string[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

function requireData<T>(data: T | null | undefined, what: string): T {
  if (data === null || data === undefined) {
    throw new Error(`${what} payload was empty.`);
  }
  return data;
}

function cleanLabel(label: string): string {
  const trimmed = label.trim();
  if (!trimmed || trimmed.length > ALIBI_LABEL_MAX) {
    throw new Error(`A case name needs 1 to ${ALIBI_LABEL_MAX} characters.`);
  }
  return trimmed;
}

export const AlibiController = {
  async list(eventId: string): Promise<AlibiPool> {
    try {
      const response = await AlibiService.list(eventId);
      return requireData(response.data, "Alibi cases");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load the cases."));
    }
  },

  async create(eventId: string, label: string): Promise<AlibiCaseDetail> {
    const clean = cleanLabel(label);
    try {
      const response = await AlibiService.create(eventId, clean);
      return requireData(response.data, "Alibi case");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not create the case."));
    }
  },

  async get(eventId: string, caseId: string): Promise<AlibiCaseDetail> {
    try {
      const response = await AlibiService.get(eventId, caseId);
      return requireData(response.data, "Alibi case");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not open the case."));
    }
  },

  async save(
    eventId: string,
    caseId: string,
    label: string,
    baseVersion: number,
    content: AlibiCaseContent,
  ): Promise<AlibiCaseDetail> {
    const clean = cleanLabel(label);
    try {
      const response = await AlibiService.save(eventId, caseId, {
        label: clean,
        baseVersion,
        content,
      });
      return requireData(response.data, "Alibi case");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not save the case."));
    }
  },

  async archive(eventId: string, caseId: string): Promise<void> {
    try {
      await AlibiService.archive(eventId, caseId);
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not remove the case."));
    }
  },

  async listRequests(eventId: string): Promise<AlibiAccessRequest[]> {
    try {
      const response = await AlibiService.listRequests(eventId);
      return requireData(response.data, "Access requests");
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load the access requests."),
      );
    }
  },

  async requestAccess(
    eventId: string,
    caseId: string,
    reason: string,
  ): Promise<AlibiAccessRequest> {
    const clean = reason.trim();
    if (!clean || clean.length > ALIBI_REASON_MAX) {
      throw new Error(`Give a reason of 1 to ${ALIBI_REASON_MAX} characters.`);
    }
    try {
      const response = await AlibiService.requestAccess(eventId, caseId, clean);
      return requireData(response.data, "Access request");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not send the request."));
    }
  },

  async decide(
    eventId: string,
    requestId: string,
    approve: boolean,
    note: string,
  ): Promise<AlibiAccessRequest> {
    try {
      const response = await AlibiService.decide(
        eventId,
        requestId,
        approve,
        note.trim(),
      );
      return requireData(response.data, "Access request");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not answer the request."));
    }
  },

  async preview(
    eventId: string,
    caseId: string,
    role: AlibiPreviewRole,
    version?: number,
  ): Promise<AlibiRolePreview> {
    try {
      const response = await AlibiService.preview(eventId, caseId, role, version);
      return requireData(response.data, "Preview");
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load the preview."));
    }
  },
};
