export interface ScheduleSummary {
  id: string;
  version: number;
  mode: "balanced" | "round_robin" | "manual" | string;
  status: "draft" | "published" | "superseded" | string;
  seed: number | null;
  createdAt: string;
}

export interface FixtureTeamView {
  teamId: string;
  teamName: string;
  side: string;
}

export interface FixtureView {
  id: string;
  slotId: string;
  eventGameId: string;
  gameName: string;
  roundNumber: number;
  status: string;
  startAt: string;
  endAt: string;
  resourceId: string | null;
  resourceName: string | null;
  teams: FixtureTeamView[];
}

export interface ScheduleDetail {
  id: string;
  eventId: string;
  version: number;
  mode: string;
  status: string;
  seed: number | null;
  fixtures: FixtureView[];
  opponentMatrix: Record<string, Record<string, number>>;
  warnings: string[];
}

export interface ResourceView {
  id: string;
  type: "room" | "table" | "station" | string;
  name: string;
  capacity: number;
}

export interface GenerateBalancedDraftRequest {
  startAt: string;
  slotMinutes: number;
  breakMinutes: number;
  seed?: number;
  eventGameIds: string[];
}

export interface AddFixtureRequest {
  eventGameId: string;
  roundNumber: number;
  startAt: string;
  endAt: string;
  teamIds: string[];
  bye: boolean;
  resourceId?: string | null;
}

export interface CreateResourceRequest {
  type: "room" | "table" | "station";
  name: string;
  capacity: number;
}