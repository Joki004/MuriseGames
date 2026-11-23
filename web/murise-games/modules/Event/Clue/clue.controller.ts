import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { ClueService } from "./clue.service";
import type { ClueBoardResponse, ClueSetSaveRequest, ReleasedCluesResponse, StageCommandRequest } from "./clue.type";

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

  async getReleased(eventId: string): Promise<ReleasedCluesResponse> {
    try {
      const res = await ClueService.getReleased(eventId);
      if (!res.data) throw new Error("Released clues were not returned.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load released clues."),
      );
    }
  },

  async releaseNext(
    eventId: string,
    payload: StageCommandRequest,
  ): Promise<ReleasedCluesResponse> {
    try {
      const res = await ClueService.releaseNext(eventId, payload);
      if (!res.data) throw new Error("Release was not confirmed.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not release the next clue stage."),
      );
    }
  },

  async closeGuessing(
    eventId: string,
    payload: StageCommandRequest,
  ): Promise<ReleasedCluesResponse> {
    try {
      const res = await ClueService.closeGuessing(eventId, payload);
      if (!res.data) throw new Error("Guessing close was not confirmed.");
      return res.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not close guessing."),
      );
    }
  },
};