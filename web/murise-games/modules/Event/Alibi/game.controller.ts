import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { AlibiGameService } from "./game.service";
import type {
  AlibiHostView,
  AlibiJudgeView,
  AlibiPlayerView,
  AlibiPublicView,
  AlibiSetupRequest,
} from "./game.type";

export const ALIBI_ANSWER_MAX = 300;

async function call<T>(
  request: Promise<{ data?: T | null }>,
  fallback: string,
): Promise<T> {
  try {
    const response = await request;
    if (response.data === null || response.data === undefined) {
      throw new Error(`${fallback} (empty response)`);
    }
    return response.data;
  } catch (error: unknown) {
    throw new Error(extractApiErrorMessage(error, fallback));
  }
}

export const AlibiGameController = {
  host: (eventId: string): Promise<AlibiHostView> =>
    call(AlibiGameService.host(eventId), "Could not load the Alibi game."),

  createSession: (eventId: string, fixtureId: string): Promise<AlibiHostView> =>
    call(AlibiGameService.createSession(eventId, fixtureId), "Could not prepare this team's slot."),

  setup: (eventId: string, roundId: string, payload: AlibiSetupRequest): Promise<AlibiHostView> =>
    call(AlibiGameService.setup(eventId, roundId, payload), "Could not save the pair and case."),

  start: (eventId: string, roundId: string): Promise<AlibiHostView> =>
    call(AlibiGameService.start(eventId, roundId), "Could not start the preparation."),

  startQuestions: (eventId: string, roundId: string): Promise<AlibiHostView> =>
    call(AlibiGameService.startQuestions(eventId, roundId), "Could not start the questions."),

  skip: (eventId: string, roundId: string): Promise<AlibiHostView> =>
    call(AlibiGameService.skip(eventId, roundId), "Could not close the question."),

  reveal: (eventId: string, roundId: string): Promise<AlibiHostView> =>
    call(AlibiGameService.reveal(eventId, roundId), "Could not reveal the next question."),

  record: (eventId: string, sessionId: string): Promise<AlibiHostView> =>
    call(AlibiGameService.record(eventId, sessionId), "Could not record the result."),

  judge: (eventId: string): Promise<AlibiJudgeView> =>
    call(AlibiGameService.judge(eventId), "Could not load the answers to judge."),

  verdict: (
    eventId: string,
    promptId: string,
    match: boolean,
    reason?: string,
  ): Promise<AlibiJudgeView> =>
    call(AlibiGameService.verdict(eventId, promptId, match, reason), "Could not save the verdict."),

  me: (eventId: string): Promise<AlibiPlayerView> =>
    call(AlibiGameService.me(eventId), "Could not load your Alibi round."),

  answer: (
    eventId: string,
    promptId: string,
    text: string,
    awayMs: number,
    awayCount: number,
  ): Promise<AlibiPlayerView> =>
    call(
      AlibiGameService.answer(
        eventId,
        promptId,
        text.slice(0, ALIBI_ANSWER_MAX),
        Math.max(0, Math.round(awayMs)),
        Math.max(0, awayCount),
      ),
      "Could not lock your answer.",
    ),

  live: (eventId: string): Promise<AlibiPublicView> =>
    call(AlibiGameService.live(eventId), "Could not load the Alibi round."),
};
