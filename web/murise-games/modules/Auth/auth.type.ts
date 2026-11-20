import type { PlatformRole } from "@/modules/User/user.type";

export interface AuthResponse {
  id: string;
  email: string | null;
  displayName: string;
  platformRoles: PlatformRole[];
  createdAt: string;
  updatedAt: string;
}

export interface ResolvedAccountResult {
  account: AuthResponse;
  requestId: string | null;
}

export interface SignUpResult {
  requiresEmailConfirmation: boolean;
}