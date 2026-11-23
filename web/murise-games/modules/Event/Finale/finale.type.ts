export type ReviewVerdict = "correct" | "wrong";

export interface ReviewDecisionRequest {
  verdict: ReviewVerdict;
}

export interface ReviewItem {
  guessId: string;
  guessingTeamId: string;
  guessingTeamName: string;
  targetTeamId: string | null;
  targetTeamName: string;
  targetTitle: string | null;
  stage: number;
  rawGuess: string;
  submittedAt: string;
  autoMatched: boolean;
  hostDecision: ReviewVerdict | null;
  /** "pending" = needs a host decision before this title can be revealed. */
  verdict: "correct" | "wrong" | "pending";
  sceneRevealed: boolean;
}

export interface ReviewBoardResponse {
  guessingStatus: "open" | "closed" | string;
  pendingCount: number;
  items: ReviewItem[];
  /** Teams whose title was recorded as leaked before the lock. */
  leakedTeamIds: string[];
}

export interface LeakRequest {
  teamId: string;
}

export interface RevealRequest {
  requestId: string;
  teamId: string;
}

export interface CorrectGuesserView {
  teamId: string;
  teamName: string;
  teamColor: string;
  stage: number;
  points: number;
}

export interface RevealView {
  sceneId: string;
  teamId: string;
  teamName: string;
  teamColor: string;
  title: string | null;
  answerScope: string | null;
  revealedAt: string;
  correctGuessers: CorrectGuesserView[];
  performerBonus: number;
  /** Title leaked before the lock: every other team got +2. */
  leaked: boolean;
}

export interface RevealListResponse {
  guessingStatus: "open" | "closed" | string;
  reveals: RevealView[];
}
