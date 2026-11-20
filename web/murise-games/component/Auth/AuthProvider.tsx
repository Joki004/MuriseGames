"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import type { Session } from "@supabase/supabase-js";
import { getSupabaseClient } from "@/config/supabase";
import { AuthController } from "../../modules/Auth/auth.controller";
import type { AuthResponse } from "../../modules/Auth/auth.type";
import type { UserResponse } from "@/modules/User/user.type";

interface AuthContextValue {
  session: Session | null;
  account: AuthResponse | null;
  lastRequestId: string | null;
  loading: boolean;
  error: string | null;
  refreshAccount: () => Promise<void>;
  applyProfileUpdate: (profile: UserResponse) => void;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [account, setAccount] = useState<AuthResponse | null>(null);
  const [lastRequestId, setLastRequestId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const syncWithBackend = useCallback(async (activeSession: Session | null) => {
    if (!activeSession?.access_token) {
      setAccount(null);
      setLastRequestId(null);
      setError(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const resolved = await AuthController.resolveBackendAccount(
        activeSession.access_token,
      );
      setAccount(resolved.account);
      setLastRequestId(resolved.requestId);
    } catch (err) {
      setAccount(null);
      setError(
        err instanceof Error
          ? err.message
          : "Failed to verify session with backend.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

useEffect(() => {
    const supabase = getSupabaseClient();
    let mounted = true;
    let lastSyncedToken: string | null = null;

    const { data: subscription } = supabase.auth.onAuthStateChange(
      (_event, nextSession) => {
        if (!mounted) return;
        const nextToken = nextSession?.access_token ?? null;
        setSession(nextSession);

        if (nextToken !== null && nextToken === lastSyncedToken) {
          return;
        }
        lastSyncedToken = nextToken;
        void syncWithBackend(nextSession);
      }
    );

    return () => {
      mounted = false;
      subscription.subscription.unsubscribe();
    };
  }, [syncWithBackend]);

  const refreshAccount = useCallback(async () => {
    const supabase = getSupabaseClient();
    const { data } = await supabase.auth.getSession();
    setSession(data.session);
    await syncWithBackend(data.session);
  }, [syncWithBackend]);

  const applyProfileUpdate = useCallback((profile: UserResponse) => {
    setAccount((prev) =>
      prev
        ? {
            ...prev,
            displayName: profile.displayName,
            platformRoles: profile.platformRoles,
            updatedAt: profile.updatedAt,
          }
        : prev,
    );
  }, []);

  const signOut = useCallback(async () => {
    await AuthController.signOut();
    setSession(null);
    setAccount(null);
    setLastRequestId(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        session,
        account,
        lastRequestId,
        loading,
        error,
        refreshAccount,
        applyProfileUpdate,
        signOut,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside AuthProvider.");
  }
  return ctx;
}
