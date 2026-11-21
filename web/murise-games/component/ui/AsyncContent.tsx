"use client";

import { useEffect, useState, type ReactNode } from "react";
import { Alert, Button, LoadingState } from "@/component/ui";

type Result<T> =
  | { status: "loading" }
  | { status: "success"; data: T }
  | { status: "error"; message: string };

interface AsyncContentProps<T> {
  load: () => Promise<T>;
  label?: string;
  children: (data: T) => ReactNode;
}

// Keep `load` stable with useCallback.
// Give this component a new key when its resource changes.
export function AsyncContent<T>({
  load,
  label = "Loading…",
  children,
}: AsyncContentProps<T>) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Result<T>>({ status: "loading" });

  useEffect(() => {
    let active = true;

    async function run() {
      try {
        const data = await load();

        if (active) {
          setResult({ status: "success", data });
        }
      } catch (error: unknown) {
        if (active) {
          setResult({
            status: "error",
            message:
              error instanceof Error
                ? error.message
                : "Something went wrong.",
          });
        }
      }
    }

    void run();

    return () => {
      active = false;
    };
  }, [load, attempt]);

  if (result.status === "loading") {
    return <LoadingState label={label} />;
  }

  if (result.status === "error") {
    return (
      <Alert tone="error" title="Unable to load">
        <p>{result.message}</p>

        <Button
          variant="secondary"
          className="mt-3"
          onClick={() => {
            setResult({ status: "loading" });
            setAttempt((value) => value + 1);
          }}
        >
          Try again
        </Button>
      </Alert>
    );
  }

  return <>{children(result.data)}</>;
}