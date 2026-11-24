export type AlibiRoundStatus =
  | "setup"
  | "preparing"
  | "questioning"
  | "judging"
  | "done";

export interface AlibiMember {
  userId: string;
  name: string;
}

export interface AlibiCaseOption {
  caseId: string;
  label: string;
}

export interface AlibiRevealedItem {
  number: number;
  question: string;
  answerA: string;
  answerB: string;
  match: boolean;
}

// ---- host ----------------------------------------------------------------

export interface AlibiRoundHost {
  roundId: string;
  pairNumber: number;
  status: AlibiRoundStatus;
  playerA: string | null;
  playerAName: string | null;
  playerB: string | null;
  playerBName: string | null;
  caseId: string | null;
  caseLabel: string | null;
  prepEndsAt: string | null;
  currentQuestion: number;
  questionCount: number;
  questionClosesAt: string | null;
  lockedA: boolean;
  lockedB: boolean;
  comparedCount: number;
  decidedCount: number;
  revealedCount: number;
  matches: number;
}

export interface AlibiTeamSlot {
  fixtureId: string;
  eventGameId: string;
  teamId: string;
  teamName: string;
  teamColor: string;
  fixtureStatus: string;
  sessionId: string | null;
  members: AlibiMember[];
  eligibleCases: AlibiCaseOption[];
  rounds: AlibiRoundHost[];
  totalMatches: number;
  recorded: boolean;
  needsRecording: boolean;
}

export interface AlibiHostView {
  serverTime: string;
  planPublished: boolean;
  teams: AlibiTeamSlot[];
}

export interface AlibiSetupRequest {
  playerA: string;
  playerB: string;
  caseId: string | null;
  random: boolean;
}

// ---- judge ---------------------------------------------------------------

export interface AlibiAnswerView {
  text: string | null;
  missing: boolean;
  awayMs: number;
  awayCount: number;
}

export interface AlibiJudgePrompt {
  promptId: string;
  number: number;
  question: string;
  validEquivalents: string[];
  judgeNote: string;
  a: AlibiAnswerView;
  b: AlibiAnswerView;
  verdict: boolean | null;
  verdictSource: "auto" | "judge" | "correction" | null;
  revealed: boolean;
}

export interface AlibiJudgeRound {
  roundId: string;
  teamName: string;
  pairNumber: number;
  status: AlibiRoundStatus;
  caseLabel: string;
  playerAName: string;
  playerBName: string;
  brief: string;
  fixedFacts: string[];
  judgeRubric: string;
  questionCount: number;
  currentQuestion: number;
  prompts: AlibiJudgePrompt[];
}

export interface AlibiJudgeView {
  serverTime: string;
  rounds: AlibiJudgeRound[];
}

// ---- player --------------------------------------------------------------

export interface AlibiPlayerQuestion {
  promptId: string;
  number: number;
  text: string;
  closesAt: string;
  locked: boolean;
  missing: boolean;
  myAnswer: string | null;
}

export interface AlibiPlayerRound {
  roundId: string;
  teamName: string;
  pairNumber: number;
  side: "A" | "B";
  partnerName: string;
  status: AlibiRoundStatus;
  prepEndsAt: string | null;
  brief: string | null;
  fixedFacts: string[] | null;
  question: AlibiPlayerQuestion | null;
  questionCount: number;
  revealed: AlibiRevealedItem[];
  matches: number;
}

export interface AlibiPlayerView {
  serverTime: string;
  round: AlibiPlayerRound | null;
}

// ---- TV ------------------------------------------------------------------

export interface AlibiPublicRound {
  teamName: string;
  teamColor: string;
  pairNumber: number;
  playerAName: string;
  playerBName: string;
  status: AlibiRoundStatus;
  prepEndsAt: string | null;
  brief: string | null;
  currentQuestion: number;
  questionCount: number;
  questionText: string | null;
  questionClosesAt: string | null;
  lockedA: boolean;
  lockedB: boolean;
  revealed: AlibiRevealedItem[];
  matches: number;
}

export interface AlibiPublicView {
  serverTime: string;
  round: AlibiPublicRound | null;
}
