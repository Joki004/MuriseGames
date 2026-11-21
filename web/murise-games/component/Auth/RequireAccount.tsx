"use client";

import { Fragment, useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "./AuthProvider";
import { Alert, Button, LoadingState } from "@/component/ui";

export function RequireAccount({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { session, account, loading, error, refreshAccount } = useAuth();

  useEffect(() => {
    if (!loading && !session) {
      router.replace("/login");
    }
  }, [loading, session, router]);

  if (loading || !session) {
    return <LoadingState label="Opening your account…" />;
  }

  if (error || !account) {
    return (
      <main className="mx-auto max-w-xl px-4 py-10">
        <Alert tone="error" title="Could not load your account">
          <p>{error ?? "Your account is temporarily unavailable."}</p>

          <Button
            variant="secondary"
            className="mt-3"
            onClick={() => void refreshAccount()}
          >
            Try again
          </Button>
        </Alert>
      </main>
    );
  }

  // Reset page state when a different account signs in.
  return <Fragment key={account.id}>{children}</Fragment>;
}