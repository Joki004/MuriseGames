"use client";

import { useRef, useState } from "react";
import { useAuth } from "@/component/Auth/AuthProvider";
import { RequireAccount } from "@/component/Auth/RequireAccount";
import { PageShell } from "@/component/Layout/PageShell";
import { ProfileCard } from "./ProfileCard";
import { AuthController } from "@/modules/Auth/auth.controller";
import {
  Alert,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/component/ui";

export function AccountView() {
  return (
    <RequireAccount>
      <AccountContent />
    </RequireAccount>
  );
}

function AccountContent() {
  const { account, applyProfileUpdate } = useAuth();
  const busy = useRef(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<{
    tone: "success" | "error";
    text: string;
  } | null>(null);

  if (!account) return null;

  const initials = account.displayName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => Array.from(part)[0] ?? "")
    .join("")
    .toUpperCase();

  async function requestPasswordReset() {
    if (!account?.email || busy.current) return;

    busy.current = true;
    setSending(true);
    setMessage(null);

    try {
      await AuthController.requestPasswordRecovery(account.email);
      setMessage({
        tone: "success",
        text: "Check your email for the password-reset link.",
      });
    } catch (error: unknown) {
      setMessage({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Could not send the password-reset email.",
      });
    } finally {
      busy.current = false;
      setSending(false);
    }
  }

  return (
    <PageShell
      title="Your account"
      description="Manage your player identity and sign-in details."
    >
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_1.5fr]">
        <Card>
          <div className="flex items-center gap-4">
            <span
              aria-hidden="true"
              className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-primary text-xl font-bold text-white"
            >
              {initials || "?"}
            </span>

            <div className="min-w-0">
              <h2 className="break-words text-lg font-semibold">
                {account.displayName}
              </h2>
              <p className="break-all text-sm text-muted">
                {account.email ?? "No email available"}
              </p>
            </div>
          </div>
        </Card>

        <div className="space-y-6">
          <ProfileCard
            initialDisplayName={account.displayName}
            onUpdated={applyProfileUpdate}
          />

          <Card>
            <CardHeader>
              <CardTitle>Sign-in settings</CardTitle>
              <CardDescription>
                Send a secure link to change your password.
              </CardDescription>
            </CardHeader>

            <Button
              variant="secondary"
              disabled={!account.email}
              loading={sending}
              loadingText="Sending email…"
              onClick={() => void requestPasswordReset()}
            >
              Send password-reset email
            </Button>

            {message && (
              <Alert tone={message.tone} className="mt-4">
                {message.text}
              </Alert>
            )}
          </Card>
        </div>
      </div>
    </PageShell>
  );
}