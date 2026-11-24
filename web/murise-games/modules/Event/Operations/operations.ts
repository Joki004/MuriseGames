import { ApiService, extractApiErrorMessage } from "@/modules/Api/api.service";

export type OfflineScoreCategory =
  | "game_points"
  | "bonus"
  | "penalty"
  | "adjustment";

export interface OfflineQueuedRow {
  id: string;
  sheetKey: string; // normalized lowercase
  rowNumber: number;
  teamId: string;
  teamName: string;
  category: OfflineScoreCategory;
  delta: number;
  reason: string;
  queuedAt: string;
  conflictDetail?: string | null;
}

export interface OfflineSyncedRowResult {
  sheetKey: string;
  rowNumber: number;
  teamId: string;
  scoreEntryId: string;
  status: "applied" | "already_synced" | "conflict";
  conflictDetail: string | null;
}

export interface OfflineSyncResponse {
  batchId: string;
  newlyAppliedCount: number;
  duplicateSkippedCount: number;
  conflictCount: number;
  results: OfflineSyncedRowResult[];
}

export interface PrintPacketResponse {
  eventId: string;
  eventName: string;
  eventTheme: string | null;
  startsAt: string;
  timezone: string;
  venue: string | null;
  scope: "host" | "public";
  generatedAt: string;
  teams: {
    id: string;
    name: string;
    color: string;
    sortOrder: number;
  }[];
  games: {
    eventGameId: string;
    name: string;
    status: string;
    houseRulesNote: string | null;
  }[];
  fixtures: {
    roundNumber: number;
    startAt: string;
    endAt: string;
    gameName: string;
    resourceName: string | null;
    teams: { teamName: string; side: string }[];
  }[];
  clues: {
    teamId: string;
    teamName: string;
    stage: number;
    body: string;
  }[];
  secretTitles: {
    teamId: string;
    teamName: string;
    title: string;
    answerScope: string;
    aliases: string[];
  }[];
  operationalNotes: string[];
}

export function normalizeSheetKey(raw: string): string {
  return raw.trim().toLowerCase();
}

export function createUuid(): string {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }
  return "00000000-0000-4000-8000-000000000000".replace(/[0]/g, () =>
    Math.floor(Math.random() * 16).toString(16),
  );
}

const STORAGE_PREFIX = "murise:offline-score-queue:";

export function loadOfflineQueue(eventId: string): OfflineQueuedRow[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(`${STORAGE_PREFIX}${eventId}`);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function saveOfflineQueue(
  eventId: string,
  rows: OfflineQueuedRow[],
): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(
    `${STORAGE_PREFIX}${eventId}`,
    JSON.stringify(rows),
  );
}

export const EventOperationsController = {
  async syncOfflineRows(
    eventId: string,
    rows: OfflineQueuedRow[],
  ): Promise<OfflineSyncResponse> {
    try {
      const payload = {
        batchId: createUuid(),
        rows: rows.map((r) => ({
          sheetKey: normalizeSheetKey(r.sheetKey),
          rowNumber: r.rowNumber,
          teamId: r.teamId,
          category: r.category,
          delta: r.delta,
          reason: r.reason.trim(),
        })),
      };
      const res = await ApiService.post<OfflineSyncResponse>(
        `/api/events/${encodeURIComponent(eventId)}/offline-sync`,
        payload,
      );
      if (!res.data) throw new Error("Empty sync response.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Failed to sync offline score rows."),
      );
    }
  },

  async fetchPrintPacket(
    eventId: string,
    scope: "public" | "host",
    includeSecrets: boolean,
  ): Promise<PrintPacketResponse> {
    try {
      const params = new URLSearchParams({
        scope,
        includeSecrets: String(includeSecrets),
      });
      const res = await ApiService.get<PrintPacketResponse>(
        `/api/events/${encodeURIComponent(eventId)}/print-packet?${params.toString()}`,
      );
      if (!res.data) throw new Error("Empty print packet response.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Failed to generate print packet."),
      );
    }
  },
};