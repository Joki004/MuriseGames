"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { AuthController } from "@/modules/Auth/auth.controller";
import {
  Alert,
  Button,
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
} from "@/component/ui";

type AuthMode = "login" | "signup" | "recovery";

interface AuthFormProps {
  returnTo?: "/dashboard" | "/join";
}

const modes: Array<{ value: AuthMode; label: string }> = [
  { value: "login", label: "Sign in" },
  { value: "signup", label: "Sign up" },
  { value: "recovery", label: "Recover" },
];

const titles: Record<AuthMode, string> = {
  login: "Welcome back",
  signup: "Create your account",
  recovery: "Recover your account",
};

const descriptions: Record<AuthMode, string> = {
  login: "Sign in to organize or join your next game night.",
  signup: "Choose a name your friends will recognize.",
  recovery: "Request an email link to reset your password.",
};

export function AuthForm({ returnTo = "/dashboard" }: AuthFormProps) {
  const router = useRouter();
  const titleId = useId();
  const busy = useRef(false);

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
    if (busy.current) return;

    setMode(nextMode);
    setPassword("");
    setMessage(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy.current) return;

    busy.current = true;
    setSubmitting(true);
    setMessage(null);

    try {
      if (mode === "login") {
        await AuthController.signIn(email, password);
        router.replace(returnTo);
        return;
      }

      if (mode === "signup") {
        const result = await AuthController.signUp(
          email,
          password,
          displayName,
          returnTo,
        );

        if (result.requiresEmailConfirmation) {
          setMessage({
            type: "success",
            text: "Check your email to confirm your account.",
          });
        } else {
          router.replace(returnTo);
        }

        return;
      }

      await AuthController.requestPasswordRecovery(email);

      setMessage({
        type: "success",
        text: "If an account uses this email, you’ll receive a password-reset link.",
      });
    } catch (error: unknown) {
      setMessage({
        type: "error",
        text: error instanceof Error ? error.message : "Authentication failed.",
      });
    } finally {
      busy.current = false;
      setSubmitting(false);
    }
  }

  return (
    <Card className="w-full max-w-md" aria-labelledby={titleId}>
      <CardHeader>
        <p className="ui-eyebrow">MuriseGames</p>
        <CardTitle
          id={titleId}
          as={returnTo === "/join" ? "h2" : "h1"}
          className="text-2xl sm:text-3xl"
        >
          {titles[mode]}
        </CardTitle>
        <CardDescription>{descriptions[mode]}</CardDescription>
      </CardHeader>

      <div
        role="group"
        aria-label="Account action"
        className="mb-6 grid grid-cols-3 gap-2"
      >
        {modes.map((item) => (
          <Button
            key={item.value}
            variant="secondary"
            className="ui-mode-button"
            aria-pressed={mode === item.value}
            disabled={submitting}
            onClick={() => switchMode(item.value)}
          >
            {item.label}
          </Button>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="space-y-5">
        <fieldset disabled={submitting} className="space-y-5">
          {mode === "signup" && (
            <Input
              label="Display name"
              name="displayName"
              autoComplete="nickname"
              required
              maxLength={80}
              placeholder="How should we call you?"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
            />
          )}

          <Input
            label="Email"
            name="email"
            type="email"
            required
            autoComplete="email"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="you@example.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />

          {mode !== "recovery" && (
            <Input
              label="Password"
              name="password"
              type="password"
              required
              minLength={6}
              autoComplete={
                mode === "login" ? "current-password" : "new-password"
              }
              hint={mode === "signup" ? "At least 6 characters." : undefined}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          )}

          <Button
            type="submit"
            fullWidth
            loading={submitting}
            loadingText={
              mode === "login"
                ? "Signing in…"
                : mode === "signup"
                  ? "Creating account…"
                  : "Sending link…"
            }
          >
            {mode === "login"
              ? "Sign in"
              : mode === "signup"
                ? "Create account"
                : "Send recovery link"}
          </Button>
        </fieldset>

        {message && (
          <Alert tone={message.type}>
            {message.text}
          </Alert>
        )}
      </form>
    </Card>
  );
}