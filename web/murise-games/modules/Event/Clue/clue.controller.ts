import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { ClueService } from "./clue.service";
import type { ClueBoardResponse, ClueSetSaveRequest } from "./clue.type";

export const CLUE_STAGES = 3;
export const CLUE_MAX_LENGTH = 280;

function normalize(input: ClueSetSaveRequest): ClueSetSaveRequest {
  if (input.clues.length !== CLUE_STAGES) {
    throw new Error("Exactly three clues are required.");
  }

  const clues = input.clues.map((item, index) => {
    const body = item.body.trim();

    if (body.length > CLUE_MAX_LENGTH) {
      throw new Error(
        `Clue ${index + 1} is longer than ${CLUE_MAX_LENGTH} characters.`,
      );
    }
    if (item.approved && !body) {
      throw new Error(`Clue ${index + 1} is empty, so it cannot be approved.`);
    }

    return { body, approved: item.approved };
  });

  return { clues };
}

export const ClueController = {
  async board(eventId: string): Promise<ClueBoardResponse> {
    try {
      const response = await ClueService.board(eventId);
      if (!response.data) throw new Error("Clue payload was empty.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not load clues."));
    }
  },

  async save(
    eventId: string,
    teamId: string,
    input: ClueSetSaveRequest,
  ): Promise<ClueBoardResponse> {
    const payload = normalize(input);

    try {
      const response = await ClueService.save(eventId, teamId, payload);
      if (!response.data) throw new Error("Clue payload was empty.");
      return response.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Could not save the clues."));
    }
  },
};