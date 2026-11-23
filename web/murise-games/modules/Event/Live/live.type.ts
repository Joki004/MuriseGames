export interface EventSnapshotResponse {
  eventId: string;
  serverTime: string;
  version: number;
  eventStatus: string;
  scoreboardVisibility: string;
  roles: string[];
  currentEventGameId: string | null;
  currentWindowStartAt?: string | null;
  phase: string;
  deadlineAt: string | null;
  pausedRemainingMs: number | null;
  paused: boolean;
  stateUpdatedAt: string | null;
  readyTeamIds: string[];
  activeFixtures?: ActiveFixtureView[];
}

export interface EventStateUpdateRequest {
  currentEventGameId: string | null;
  windowStartAt?: string | null;
  phase: string;
  durationSeconds: number | null;
}


export interface ActiveFixtureTeamView {
  teamId: string;
  teamName: string;
  teamColor: string;
  side: string;
}

export interface ActiveFixtureView {
  fixtureId: string;
  eventGameId: string;
  gameName: string;
  gameCode: string;
  roundNumber: number;
  status: string;
  startAt: string;
  endAt: string;
  resourceName: string | null;
  teams: ActiveFixtureTeamView[];
}




