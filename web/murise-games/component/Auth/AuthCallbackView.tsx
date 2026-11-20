"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import type { EmailOtpType } from "@supabase/supabase-js";
import { getSupabaseClient } from "@/config/supabase";

const inFlightCallbacks = new Map<string, Promise<string | null>>();

function runOnce(
  key: string,
  operation: () => Promise<string | null>
): Promise<string | null> {
  const existing = inFlightCallbacks.get(key);
  if (existing) {
    return existing;
  }

  const promise = operation();
  inFlightCallbacks.set(key, promise);
  return promise;
}

export function AuthCallbackView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const supabase = getSupabaseClient();

    const errorDescription =
      searchParams.get("error_description") ?? searchParams.get("error");
    const code = searchParams.get("code");
    const tokenHash = searchParams.get("token_hash");
    const type = searchParams.get("type");
    const nextParam = searchParams.get("next");
    const safeNext =
      nextParam && nextParam.startsWith("/") && !nextParam.startsWith("//")
        ? nextParam
        : "/dashboard";

    async function completeAuth() {
      if (errorDescription) {
        if (active) setError(errorDescription);
        return;
      }

      if (tokenHash && type) {
        const errMessage = await runOnce(`otp:${type}:${tokenHash}`, async () => {
          const { error: otpError } = await supabase.auth.verifyOtp({
            token_hash: tokenHash,
            type: type as EmailOtpType,
          });
          return otpError ? otpError.message : null;
        });

        if (!active) return;
        if (errMessage) {
          setError(errMessage);
          return;
        }
        router.replace(safeNext);
        return;
      }

      if (code) {
        const errMessage = await runOnce(`pkce:${code}`, async () => {
          const { error: exchangeError } =
            await supabase.auth.exchangeCodeForSession(code);
          return exchangeError ? exchangeError.message : null;
        });

        if (!active) return;
        if (errMessage) {
          setError(errMessage);
          return;
        }
        router.replace(safeNext);
        return;
      }

      const { data } = await supabase.auth.getSession();
      if (!active) return;
      router.replace(data.session ? safeNext : "/login");
    }

    void completeAuth();

    return () => {
      active = false;
    };
  }, [router, searchParams]);

  if (error) {
    return (
      <div className="max-w-md rounded-xl border border-rose-800 bg-rose-950/50 p-6 text-sm text-rose-200">
        <p className="font-semibold">Authentication callback failed</p>
        <p className="mt-2">{error}</p>
        <button
          type="button"
          onClick={() => router.replace("/login")}
          className="mt-4 rounded-lg bg-zinc-800 px-3 py-1.5 text-xs text-zinc-100 hover:bg-zinc-700"
        >
          Back to Sign In
        </button>
      </div>
    );
  }

  return <p className="text-sm text-zinc-400">Completing sign-in...</p>;
}