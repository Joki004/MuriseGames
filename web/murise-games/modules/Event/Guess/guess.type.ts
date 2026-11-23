export interface SubmitGuessRequest {
  requestId: string;
  targetTeamId: string;
  expectedStage: number;
  expectedVersion: number;
  guess: string;
}

export interface GuessReceipt {
  id: string;
  targetTeamId: string;
  targetTeamName: string;
  stage: number;
  rawGuess: string;
  submittedAt: string;
  receiptStatus: string;
}

export interface OpposingTarget {
  teamId: string;
  teamName: string;
  teamColor: string;
  hasRegisteredScene: boolean;
}

export interface CaptainGuessBoardResponse {
  guessingTeamId: string | null;
  guessingTeamName: string | null;
  captain: boolean;
  currentStage: number;
  guessingStatus: "open" | "closed" | string;
  version: number;
  targets: OpposingTarget[];
  submittedGuesses: GuessReceipt[];
}