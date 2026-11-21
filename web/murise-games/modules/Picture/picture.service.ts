import { ApiService } from "@/modules/Api/api.service";
import type { PictureResponse, PictureSlot } from "./picture.type";

function path(slot: PictureSlot, targetId: string) {
  return `/api/pictures/${slot}/${encodeURIComponent(targetId)}`;
}

export const PictureService = {
  get(slot: PictureSlot, targetId: string) {
    return ApiService.get<PictureResponse>(path(slot, targetId));
  },

  chooseEmoji(slot: PictureSlot, targetId: string, emoji: string) {
    return ApiService.put<boolean>(
      `${path(slot, targetId)}/emoji`,
      { emoji },
    );
  },

  upload(slot: PictureSlot, targetId: string, file: Blob) {
    const body = new FormData();
    body.append("file", file, "picture.jpg");

    return ApiService.post<boolean>(
      `${path(slot, targetId)}/image`,
      body,
    );
  },

  remove(slot: PictureSlot, targetId: string) {
    return ApiService.delete<boolean>(path(slot, targetId));
  },
};