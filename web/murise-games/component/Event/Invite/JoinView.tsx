"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AuthForm } from "@/component/Auth/AuthForm";
import { useAuth } from "@/component/Auth/AuthProvider";
import { InviteController } from "@/modules/Event/Invite/invite.controller";
import type { InviteRedeemResponse } from "@/modules/Event/Invite/invite.type";
import {
  capturePendingInvite,
  clearPendingInvite,
} from "@/modules/Event/Invite/pending-invite";

export function JoinView() {
  const { session, account, loading, error, refreshAccount } = useAuth();

  const [invite, setInvite] = useState<{
    ready: boolean;
    token: string | null;
  }>({ ready: false, token: null });

  useEffect(() => {
    let active = true;

    function capture() {
      if (active) {
        setInvite({
          ready: true,
          token: capturePendingInvite(),
        });
      }
    }

    // Capture browser-only state after hydration.
    void Promise.resolve().then(capture);
    window.addEventListener("hashchange", capture);

    return () => {
      active = false;
      window.removeEventListener("hashchange", capture);
    };
  }, []);

  if (!invite.ready || loading) {
    return (
      <main className="mx-auto max-w-lg px-4 py-12">
        <p role="status" className="text-sm text-zinc-400">
          Preparing your invitation…
        </p>
      </main>
    );
  }

  if (!invite.token) {
    return (
      <main className="mx-auto max-w-lg space-y-4 px-4 py-12">
        <h1 className="text-2xl font-bold">Open your invitation link</h1>
        <p className="text-sm text-zinc-400">
          No invitation is available in this tab. Reopen the link from your
          organizer.
        </p>
        <Link href="/dashboard" className="text-emerald-400">
          Go to dashboard
        </Link>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="mx-auto max-w-lg space-y-5 px-4 py-12">
        <div>
          <h1 className="text-2xl font-bold">Join a game night</h1>
          <p className="mt-2 text-sm text-zinc-400">
            Sign in or create an account to continue. After confirming your
            email, return to this tab or reopen the invitation link.
          </p>
        </div>

        <AuthForm returnTo="/join" />
      </main>
    );
  }

  if (!account || account.id !== session.user.id) {
    return (
      <main className="mx-auto max-w-lg space-y-4 px-4 py-12">
        <p role="alert" className="text-sm text-rose-300">
          {error ?? "Your account could not be verified."}
        </p>
        <button
          type="button"
          onClick={() => void refreshAccount()}
          className="rounded-lg border border-zinc-700 px-4 py-2"
        >
          Retry verification
        </button>
      </main>
    );
  }

  return (
    <JoinAction
      key={`${account.id}:${invite.token}`}
      token={invite.token}
      displayName={account.displayName}
    />
  );
}

function JoinAction({
  token,
  displayName,
}: {
  token: string;
  displayName: string;
}) {
  const busy = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<InviteRedeemResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function join() {
    if (busy.current) return;

    busy.current = true;
    setSubmitting(true);
    setError(null);

    try {
      const response = await InviteController.redeem(token);
      setResult(response);

      if (response.status === "JOINED") {
        clearPendingInvite(token);
      }
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not process invitation.",
      );
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto max-w-lg px-4 py-12">
      <section className="space-y-4 rounded-2xl border border-zinc-800 bg-zinc-900/70 p-6">
        <p className="text-xs font-semibold uppercase tracking-widest text-emerald-400">
          MuriseGames
        </p>

        {!result && (
          <>
            <h1 className="text-2xl font-bold">Join a game night</h1>
            <p className="text-sm text-zinc-300">
              Continuing as <strong>{displayName}</strong>.
            </p>
            <p className="text-sm text-zinc-400">
              Public events admit you immediately. Private events require
              organizer approval.
            </p>

            <button
              type="button"
              disabled={submitting}
              onClick={() => void join()}
              className="rounded-lg bg-emerald-600 px-4 py-2 font-semibold
                text-white hover:bg-emerald-500 disabled:opacity-50"
            >
              {submitting ? "Submitting…" : "Join event"}
            </button>
          </>
        )}

        {result?.status === "JOINED" && (
          <div role="status" className="space-y-3">
            <h1 className="text-2xl font-bold">You have access</h1>
            <p className="text-sm text-zinc-300">
              The event is available on your dashboard. If you are a new
              player, your organizer will assign your team.
            </p>
          </div>
        )}

        {result?.status === "PENDING" && (
          <div role="status" className="space-y-3">
            <h1 className="text-2xl font-bold">You’re in the waiting room</h1>
            <p className="text-sm text-zinc-300">
              Your request has been saved. The event will appear on your
              dashboard once an organizer approves you.
            </p>
          </div>
        )}

        {error && (
          <p role="alert" className="text-sm text-rose-300">
            {error}
          </p>
        )}

        <Link
          href="/dashboard"
          className="block text-sm text-emerald-400 hover:underline"
        >
          Go to dashboard
        </Link>
      </section>
    </main>
  );
}