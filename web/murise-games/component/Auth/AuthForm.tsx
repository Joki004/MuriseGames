"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import {AuthController} from "@/modules/Auth/auth.controller";
    
    type AuthMode = "login" | "signup" | "recovery";

export function AuthForm() {
  const router = useRouter();
  const [mode, setMode] = useState<AuthMode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  function switchMode(nextMode: AuthMode) {
    setMode(nextMode);
    setMessage(null);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setMessage(null);

    try {
      if (mode === "login") {
        await AuthController.signIn(email, password);
        router.replace("/dashboard");
        return;
      }

      if (mode === "signup") {
        const result = await AuthController.signUp(email, password, displayName);
        if (result.requiresEmailConfirmation) {
          setMessage({
            type: "success",
            text: "Account created. Check your email for the confirmation link.",
          });
        } else {
          router.replace("/dashboard");
        }
        return;
      }

      await AuthController.requestPasswordRecovery(email);
      setMessage({
        type: "success",
        text: "Recovery link sent. Check your inbox to reset your password.",
      });
    } catch (err) {
      setMessage({
        type: "error",
        text: err instanceof Error ? err.message : "Authentication failed.",
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="w-full max-w-md rounded-2xl border border-zinc-800 bg-zinc-900/80 p-8 shadow-xl">
      <div className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-widest text-emerald-400">
          MuriseGames
        </p>
        <h1 className="mt-1 text-2xl font-bold text-zinc-100">
          {mode === "login" && "Sign in to your account"}
          {mode === "signup" && "Create your account"}
          {mode === "recovery" && "Reset your password"}
        </h1>
      </div>

      <div className="mb-6 grid grid-cols-3 gap-1 rounded-lg bg-zinc-950 p-1 text-xs font-medium">
        {(["login", "signup", "recovery"] as const).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => switchMode(tab)}
            className={`rounded-md py-2 capitalize transition ${
              mode === tab
                ? "bg-zinc-800 text-zinc-100"
                : "text-zinc-400 hover:text-zinc-200"
            }`}
          >
            {tab === "recovery" ? "Recover" : tab}
          </button>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {mode === "signup" && (
          <div>
            <label
              htmlFor="displayName"
              className="block text-sm font-medium text-zinc-300"
            >
              Display Name
            </label>
            <input
              id="displayName"
              type="text"
              required
              maxLength={80}
              placeholder="e.g. Joram"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 outline-none focus:border-emerald-500"
            />
          </div>
        )}

        <div>
          <label
            htmlFor="email"
            className="block text-sm font-medium text-zinc-300"
          >
            Email
          </label>
          <input
            id="email"
            type="email"
            required
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1.5 w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 outline-none focus:border-emerald-500"
          />
        </div>

        {mode !== "recovery" && (
          <div>
            <label
              htmlFor="password"
              className="block text-sm font-medium text-zinc-300"
            >
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              minLength={6}
              autoComplete={
                mode === "login" ? "current-password" : "new-password"
              }
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1.5 w-full rounded-lg border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-zinc-100 outline-none focus:border-emerald-500"
            />
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-500 disabled:opacity-50"
        >
          {submitting
            ? "Please wait..."
            : mode === "login"
              ? "Sign In"
              : mode === "signup"
                ? "Create Account"
                : "Send Recovery Email"}
        </button>

        {message && (
          <p
            className={`rounded-lg border px-3 py-2 text-sm ${
              message.type === "success"
                ? "border-emerald-800 bg-emerald-950/50 text-emerald-300"
                : "border-rose-800 bg-rose-950/50 text-rose-300"
            }`}
          >
            {message.text}
          </p>
        )}
      </form>
    </div>
  );
}