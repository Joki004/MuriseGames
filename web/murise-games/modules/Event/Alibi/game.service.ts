import { ApiService } from "@/modules/Api/api.service";
import type {
  AlibiHostView,
  AlibiJudgeView,
  AlibiPlayerView,
  AlibiPublicView,
  AlibiSetupRequest,
} from "./game.type";

function base(eventId: string): string {
  return `/api/events/${encodeURIComponent(eventId)}/alibi`;
}

export const AlibiGameService = {
  host: (eventId: string) => ApiService.get<AlibiHostView>(`${base(eventId)}/host`),

  createSession: (eventId: string, fixtureId: string) =>
    ApiService.post<AlibiHostView>(`${base(eventId)}/sessions`, { fixtureId }),

  setup: (eventId: string, roundId: string, payload: AlibiSetupRequest) =>
    ApiService.put<AlibiHostView>(
      `${base(eventId)}/rounds/${encodeURIComponent(roundId)}/setup`,
      payload,
    ),

  start: (eventId: string, roundId: string) =>
    ApiService.post<AlibiHostView>(
      `${base(eventId)}/rounds/${encodeURIComponent(roundId)}/start`,
    ),

  startQuestions: (eventId: string, roundId: string) =>
    ApiService.post<AlibiHostView>(
      `${base(eventId)}/rounds/${encodeURIComponent(roundId)}/questions`,
    ),

  skip: (eventId: string, roundId: string) =>
    ApiService.post<AlibiHostView>(
      `${base(eventId)}/rounds/${encodeURIComponent(roundId)}/skip`,
    ),

  reveal: (eventId: string, roundId: string) =>
    ApiService.post<AlibiHostView>(
      `${base(eventId)}/rounds/${encodeURIComponent(roundId)}/reveal`,
    ),

  record: (eventId: string, sessionId: string) =>
    ApiService.post<AlibiHostView>(
      `${base(eventId)}/sessions/${encodeURIComponent(sessionId)}/record`,
    ),

  judge: (eventId: string) => ApiService.get<AlibiJudgeView>(`${base(eventId)}/judge`),

  verdict: (eventId: string, promptId: string, match: boolean, reason?: string) =>
    ApiService.put<AlibiJudgeView>(
      `${base(eventId)}/prompts/${encodeURIComponent(promptId)}/verdict`,
      { match, reason: reason ?? null },
    ),

  me: (eventId: string) => ApiService.get<AlibiPlayerView>(`${base(eventId)}/me`),

  answer: (
    eventId: string,
    promptId: string,
    text: string,
    awayMs: number,
    awayCount: number,
  ) =>
    ApiService.post<AlibiPlayerView>(
      `${base(eventId)}/prompts/${encodeURIComponent(promptId)}/answer`,
      { text, awayMs, awayCount },
    ),

  live: (eventId: string) => ApiService.get<AlibiPublicView>(`${base(eventId)}/live`),
};
