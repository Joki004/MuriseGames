export type SocialRoleCardType = "DEFENDER" | "ATTACKER" | "JUDGE" | "AUDIENCE";

export interface SocialJudgeRubric {
  summary: string;
  criteria: string[];
}

export interface SocialCaseContent {
  publicSetup: string;
  defenderGoals: string[];
  defenderConstraints: string[];
  attackerRedFlags: string[];
  attackerBait: string[];
  judgeRubric: SocialJudgeRubric;
}

export interface SocialCaseSummary {
  id: string;
  label: string;
  sortOrder: number;
  currentVersion: number;
  ready: boolean;
  updatedAt: string;
  lastEditedBy: string;
  writtenBy: string;
  seenByMe: boolean;
  canOpen: boolean;
  myRequestStatus: "pending" | "approved" | "refused" | null;
}

export interface SocialPoolResponse {
  cases: SocialCaseSummary[];
  canApprove: boolean;
  pendingRequests: number;
}

export interface SocialVersionInfo {
  versionNumber: number;
  createdAt: string;
  createdBy: string;
}

export interface SocialCaseDetail {
  id: string;
  label: string;
  currentVersion: number;
  content: SocialCaseContent;
  ready: boolean;
  missing: string[];
  versions: SocialVersionInfo[];
  seenBy: string[];
}

export interface SocialVersionDetail {
  versionNumber: number;
  createdAt: string;
  createdBy: string;
  content: SocialCaseContent;
}

export interface SocialAccessRequestView {
  id: string;
  caseId: string;
  caseLabel: string;
  requester: string;
  mine: boolean;
  reason: string;
  status: "pending" | "approved" | "refused";
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface SocialRoleAssignmentRequest {
  roundNumber: number;
  fixtureId?: string | null;
  caseId: string;
  versionNumber?: number | null;
  userId: string;
  teamId?: string | null;
  role: SocialRoleCardType;
}

export interface SocialRoleAssignmentView {
  id: string;
  roundNumber: number;
  fixtureId: string | null;
  caseId: string;
  versionNumber: number;
  userId: string;
  teamId: string | null;
  role: SocialRoleCardType;
  active: boolean;
}

export interface SocialRoleCardView {
  role: SocialRoleCardType;
  caseId: string | null;
  versionNumber: number;
  label: string;
  publicSetup: string;
  defenderGoals: string[] | null;
  defenderConstraints: string[] | null;
  attackerRedFlags: string[] | null;
  attackerBait: string[] | null;
  judgeRubric: SocialJudgeRubric | null;
  rawJudgeRubric: Record<string, unknown> | null;
}