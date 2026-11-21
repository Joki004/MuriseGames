import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { PictureService } from "./picture.service";
import type { PictureResponse, PictureSlot } from "./picture.type";

async function command(operation: () => Promise<unknown>): Promise<void> {
  try {
    await operation();
  } catch (error: unknown) {
    throw new Error(
      extractApiErrorMessage(error, "Could not update the picture."),
    );
  }
}

export const PictureController = {
  async get(
    slot: PictureSlot,
    targetId: string,
  ): Promise<PictureResponse> {
    try {
      const response = await PictureService.get(slot, targetId);

      if (!response.data) {
        throw new Error("The picture response was empty.");
      }

      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Could not load the picture."),
      );
    }
  },

  chooseEmoji(slot: PictureSlot, targetId: string, emoji: string) {
    return command(() =>
      PictureService.chooseEmoji(slot, targetId, emoji),
    );
  },

  upload(slot: PictureSlot, targetId: string, file: Blob) {
    return command(() =>
      PictureService.upload(slot, targetId, file),
    );
  },

  remove(slot: PictureSlot, targetId: string) {
    return command(() =>
      PictureService.remove(slot, targetId),
    );
  },
};