import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { UserService } from "./user.service";
import type { UserResponse } from "./user.type";

const MAX_DISPLAY_NAME_LENGTH = 80;

export const UserController = {
  async getProfile(): Promise<UserResponse> {
    try {
      const response = await UserService.getMe();
      if (!response.data) {
        throw new Error("Profile payload was empty.");
      }
      return response.data;
    } catch (error: unknown) {
      throw new Error(extractApiErrorMessage(error, "Failed to load profile."));
    }
  },

  async updateDisplayName(displayName: string): Promise<UserResponse> {
    const trimmed = displayName.trim();
    if (!trimmed) {
      throw new Error("Display name is required.");
    }
    if (trimmed.length > MAX_DISPLAY_NAME_LENGTH) {
      throw new Error(
        `Display name must be at most ${MAX_DISPLAY_NAME_LENGTH} characters.`
      );
    }

    try {
      const response = await UserService.updateMe({ displayName: trimmed });
      if (!response.data) {
        throw new Error("Updated profile payload was empty.");
      }
      return response.data;
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Failed to update display name.")
      );
    }
  },
};