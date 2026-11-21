export interface EventSnapshotResponse {
  eventId: string;
  serverTime: string;
  version: number;
  eventStatus: string;
  scoreboardVisibility: string;
  roles: string[];
  currentEventGameId: string | null;
  phase: string;
  deadlineAt: string | null;
  pausedRemainingMs: number | null;
  paused: boolean;
  stateUpdatedAt: string | null;
}

export interface EventStateUpdateRequest {
  currentEventGameId: string | null;
  phase: string;
  durationSeconds: number | null;
}