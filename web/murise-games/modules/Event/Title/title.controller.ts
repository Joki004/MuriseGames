import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { TitleService } from "./title.service";
import type {
  AnswerScope,
  TitleBoardResponse,
  TitleSaveRequest,
} from "./title.type";

export const TITLE_MAX_LENGTH = 120;
export const MAX_ALIASES = 10;

export const ANSWER_SCOPES: { value: AnswerScope; label: string }[] = [
  { value: "film", label: "Film" },
  { value: "show", label: "TV show" },
  { value: "stage_play", label: "Stage play" },
  { value: "franchise", label: "Franchise (any entry)" },
];

/** One alias per line; blank lines are ignored. */
export function parseAliases(text: string): string[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

function normalize(input: TitleSaveRequest): TitleSaveRequest {
  const title = input.title.trim();

  if (!title || title.length > TITLE_MAX_LENGTH) {
    throw new Error(`The title must have 1 to ${TITLE_MAX_LENGTH} characters.`);
  }
  if (!ANSWER_SCOPES.some((scope) => scope.value === input.answerScope)) {
    throw new Error("Choose an answer scope.");
  }

  const aliases = input.aliases.map((alias) => alias.trim()).filter(Boolean);

  if (aliases.length > MAX_ALIASES) {
    throw new Error(`Use at most ${MAX_ALIASES} aliases.`);
  }
  if (aliases.some((alias) => alias.length > TITLE_MAX_LENGTH)) {
    throw new Error(
      `An alias can have at most ${TITLE_MAX_LENGTH} characters.`,
    );
  }

  return { title, answerScope: input.answerScope, aliases };
}

export const TitleController = {
  async board(eventId: string): Promise<TitleBoardResponse> {
    try {
      const response = await TitleService.board(eventId);
      if (!response.data) throw new Error("Title payload was empty.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load titles."));
    }
  },

  async save(
    eventId: string,
    teamId: string,
    input: TitleSaveRequest,
  ): Promise<TitleBoardResponse> {
    const payload = normalize(input);

    try {
      const response = await TitleService.save(eventId, teamId, payload);
      if (!response.data) throw new Error("Title payload was empty.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not save the title."),
      );
    }
  },
};