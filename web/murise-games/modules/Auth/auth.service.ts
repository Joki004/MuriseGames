import { getSupabaseClient } from "@/config/supabase";
import { ApiService } from "@/modules/Api/api.service";
import type { CustomApiResponse } from "@/modules/Api/api.type";
import type { AuthResponse, SignUpResult } from "./auth.type";

export const AuthService = {
  getMe(accessToken?: string): Promise<CustomApiResponse<AuthResponse>> {
    const headers = accessToken
      ? { Authorization: `Bearer ${accessToken}` }
      : undefined;
    return ApiService.get<AuthResponse>("/api/auth/me", headers);
  },

  async signInWithPassword(email: string, password: string): Promise<void> {
    const supabase = getSupabaseClient();
    const { error } = await supabase.auth.signInWithPassword({
      email,
      password,
    });
    if (error) {
      throw new Error(error.message);
    }
  },

  async signUpWithPassword(
    email: string,
    password: string,
    displayName: string,
    redirectTo: string
  ): Promise<SignUpResult> {
    const supabase = getSupabaseClient();
    const { data, error } = await supabase.auth.signUp({
      email,
      password,
      options: {
        emailRedirectTo: redirectTo,
        // Initial display_name hint only; backend never trusts metadata for roles.
        data: { display_name: displayName },
      },
    });

    if (error) {
      throw new Error(error.message);
    }

    return { requiresEmailConfirmation: !data.session };
  },

  async sendPasswordResetEmail(
    email: string,
    redirectTo: string
  ): Promise<void> {
    const supabase = getSupabaseClient();
    const { error } = await supabase.auth.resetPasswordForEmail(email, {
      redirectTo,
    });
    if (error) {
      throw new Error(error.message);
    }
  },

  async updatePassword(newPassword: string): Promise<void> {
    const supabase = getSupabaseClient();
    const { error } = await supabase.auth.updateUser({ password: newPassword });
    if (error) {
      throw new Error(error.message);
    }
  },

  async signOut(): Promise<void> {
    const supabase = getSupabaseClient();
    const { error } = await supabase.auth.signOut();
    if (error) {
      throw new Error(error.message);
    }
  },
};