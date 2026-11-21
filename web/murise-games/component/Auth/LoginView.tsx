"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { AuthForm } from "./AuthForm";
import { useAuth } from "./AuthProvider";

export function LoginView() {
  const router = useRouter();
  const { session, loading } = useAuth();

  useEffect(() => {
    if (!loading && session) {
      router.replace("/dashboard");
    }
  }, [loading, session, router]);

  return (
   <main className="ui-auth-page">
      <AuthForm />
    </main>
  );
}