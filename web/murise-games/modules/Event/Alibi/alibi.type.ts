export interface AlibiQuestion {
  prompt: string;
  validEquivalents: string[];
  judgeNote: string;
}

export interface AlibiCaseContent {
  brief: string;
  fixedFacts: string[];
  questions: AlibiQuestion[];
  judgeRubric: string;
}

export interface AlibiCaseSummary {
  id: string;
  label: string;
  sortOrder: number;
  currentVersion: number;
  ready: boolean;
  questionCount: number;
  fixedFactCount: number;
  updatedAt: string;
  lastEditedBy: string;
  writtenBy: string;
  /** You already know this case (wrote, edited or opened it). */
  seenByMe: boolean;
  /** You wrote it, you created the event, or your request was approved. */
  canOpen: boolean;
  myRequestStatus: AlibiRequestStatus | null;
}

export interface AlibiPool {
  cases: AlibiCaseSummary[];
  /** Only the event creator answers access requests. */
  canApprove: boolean;
  pendingRequests: number;
}

export type AlibiRequestStatus = "pending" | "approved" | "refused";

export interface AlibiAccessRequest {
  id: string;
  caseId: string;
  caseLabel: string;
  requester: string;
  mine: boolean;
  reason: string;
  status: AlibiRequestStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface AlibiVersionInfo {
  versionNumber: number;
  createdAt: string;
  createdBy: string;
}

export interface AlibiCaseDetail {
  id: string;
  label: string;
  currentVersion: number;
  content: AlibiCaseContent;
  ready: boolean;
  missing: string[];
  versions: AlibiVersionInfo[];
  seenBy: string[];
}

export interface AlibiCaseSaveRequest {
  label: string;
  baseVersion: number;
  content: AlibiCaseContent;
}

export type AlibiPreviewRole = "audience" | "pair" | "judge";

export interface AlibiQuestionView {
  number: number;
  prompt: string;
  validEquivalents: string[] | null;
  judgeNote: string | null;
}

/** Fields a role may not see are null. */
export interface AlibiRolePreview {
  role: "AUDIENCE" | "PAIR" | "JUDGE";
  versionNumber: number;
  label: string;
  brief: string;
  fixedFacts: string[] | null;
  questions: AlibiQuestionView[] | null;
  judgeRubric: string | null;
}
