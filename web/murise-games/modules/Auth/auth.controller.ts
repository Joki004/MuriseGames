import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { AuthService } from "./auth.service";
import type { ResolvedAccountResult, SignUpResult } from "./auth.type";

export const AuthController = {
  async signIn(email: string, password: string): Promise<void> {
    const normalizedEmail = email.trim();
    if (!normalizedEmail || !password) {
      throw new Error("Email and password are required.");
    }
    await AuthService.signInWithPassword(normalizedEmail, password);
  },

  async signUp(
    email: string,
    password: string,
    displayName: string,
    returnTo: "/dashboard" | "/join" = "/dashboard",
  ): Promise<SignUpResult> {
    const normalizedEmail = email.trim();
    const normalizedName = displayName.trim();

    if (!normalizedName) {
      throw new Error("Display name is required.");
    }
    if (normalizedName.length > 80) {
      throw new Error("Display name must be at most 80 characters.");
    }
    if (password.length < 6) {
      throw new Error("Password must be at least 6 characters.");
    }

    const redirectTo = `${window.location.origin}/auth/callback?next=${encodeURIComponent(returnTo)}`;
    return AuthService.signUpWithPassword(
      normalizedEmail,
      password,
      normalizedName,
      redirectTo,
    );
  },

  async requestPasswordRecovery(email: string): Promise<void> {
    const normalizedEmail = email.trim();
    if (!normalizedEmail) {
      throw new Error("Email is required.");
    }
    const redirectTo = `${window.location.origin}/auth/callback?next=/reset-password`;
    await AuthService.sendPasswordResetEmail(normalizedEmail, redirectTo);
  },

  async completePasswordReset(
    newPassword: string,
    confirmPassword: string,
  ): Promise<void> {
    if (newPassword.length < 6) {
      throw new Error("Password must be at least 6 characters.");
    }
    if (newPassword !== confirmPassword) {
      throw new Error("Passwords do not match.");
    }
    await AuthService.updatePassword(newPassword);
  },

  async signOut(): Promise<void> {
    await AuthService.signOut();
  },

  async resolveBackendAccount(
    accessToken?: string,
  ): Promise<ResolvedAccountResult> {
    try {
      const response = await AuthService.getMe(accessToken);
      if (!response.data) {
        throw new Error("Backend returned an empty account payload.");
      }
      return {
        account: response.data,
        requestId: response.requestId ?? null,
      };
    } catch (error: unknown) {
      throw new Error(
        extractApiErrorMessage(error, "Failed to verify session with backend."),
      );
    }
  },
};
