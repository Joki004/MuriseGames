export type PlatformRole = "ADMIN";

export interface UserResponse {
  id: string;
  displayName: string;
  platformRoles: PlatformRole[];
  createdAt: string;
  updatedAt: string;
}

export interface UserUpdateRequest {
  displayName: string;
}