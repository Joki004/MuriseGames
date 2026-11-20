import { ApiService } from "@/modules/Api/api.service";
import type { CustomApiResponse } from "@/modules/Api/api.type";
import type { UserResponse, UserUpdateRequest } from "./user.type";

export const UserService = {
  getMe(): Promise<CustomApiResponse<UserResponse>> {
    return ApiService.get<UserResponse>("/api/users/me");
  },

  updateMe(
    payload: UserUpdateRequest
  ): Promise<CustomApiResponse<UserResponse>> {
    return ApiService.patch<UserResponse>("/api/users/me", payload);
  },
};