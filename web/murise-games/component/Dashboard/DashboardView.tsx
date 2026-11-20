"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/component/Auth/AuthProvider";
import { ProfileCard } from "@/component/User/ProfileCard";

export function DashboardView() {
  const router = useRouter();
  const {
    session,
    account,
    lastRequestId,
    loading,
    error,
    refreshAccount,
    applyProfileUpdate,
    signOut,
  } = useAuth();

  useEffect(() => {
    if (!loading && !session) {
      router.replace("/login");
    }
  }, [loading, session, router]);

  async function handleSignOut() {
    await signOut();
    router.replace("/login");
  }

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center text-sm text-zinc-400">
        Verifying Supabase token with Spring Boot...
      </main>
    );
  }

  if (!session) {
    return null;
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <header className="flex items-center justify-between border-b border-zinc-800 pb-5">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-emerald-400">
            MuriseGames · Pass 1 Verified
          </p>
          <h1 className="mt-1 text-2xl font-bold text-zinc-100">
            {account ? `Welcome, ${account.displayName}` : "Dashboard"}
          </h1>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => void refreshAccount()}
            className="rounded-lg border border-zinc-700 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-800"
          >
            Re-verify /api/auth/me
          </button>
          <button
            type="button"
            onClick={() => void handleSignOut()}
            className="rounded-lg bg-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-100 hover:bg-zinc-700"
          >
            Sign Out
          </button>
        </div>
      </header>

      {error && (
        <div className="mt-6 rounded-xl border border-rose-800 bg-rose-950/50 p-4 text-sm text-rose-200">
          <p className="font-semibold">Backend verification failed</p>
          <p className="mt-1">{error}</p>
        </div>
      )}

      {account && (
        <div className="mt-6 space-y-6">
          <section className="rounded-xl border border-zinc-800 bg-zinc-900/70 p-6">
            <h2 className="text-lg font-semibold text-zinc-100">
              Resolved Account{" "}
              <span className="text-xs font-normal text-zinc-400">
                GET /api/auth/me
              </span>
            </h2>

            <dl className="mt-4 grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-zinc-400">User ID (Supabase sub)</dt>
                <dd className="mt-0.5 break-all font-mono text-xs text-zinc-200">
                  {account.id}
                </dd>
              </div>
              <div>
                <dt className="text-zinc-400">Email</dt>
                <dd className="mt-0.5 text-zinc-200">{account.email ?? "—"}</dd>
              </div>
              <div>
                <dt className="text-zinc-400">DB Platform Roles</dt>
                <dd className="mt-0.5 text-zinc-200">
                  {account.platformRoles.length > 0
                    ? account.platformRoles.join(", ")
                    : "USER (no elevated grants)"}
                </dd>
              </div>
              <div>
                <dt className="text-zinc-400">Last X-Request-ID</dt>
                <dd className="mt-0.5 break-all font-mono text-xs text-zinc-400">
                  {lastRequestId ?? "—"}
                </dd>
              </div>
              <div>
                <dt className="text-zinc-400">Profile Created</dt>
                <dd className="mt-0.5 text-xs text-zinc-300">
                  {account.createdAt}
                </dd>
              </div>
              <div>
                <dt className="text-zinc-400">Profile Updated</dt>
                <dd className="mt-0.5 text-xs text-zinc-300">
                  {account.updatedAt}
                </dd>
              </div>
            </dl>
          </section>

          <ProfileCard
            initialDisplayName={account.displayName}
            onUpdated={applyProfileUpdate}
          />
        </div>
      )}
    </main>
  );
}