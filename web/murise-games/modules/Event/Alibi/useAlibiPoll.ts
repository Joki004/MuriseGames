"use client";

import { useCallback, useEffect, useState } from "react";

interface PollState<T> {
  data: T | null;
  error: string | null;
  /** Server clock minus local clock, from the response's serverTime. */
  offsetMs: number;
}

/**
 * Polls a view every `intervalMs` while the tab is visible. `load` must be stable
 * (wrap it in useCallback). State is only set inside promise callbacks.
 */
export function useAlibiPoll<T extends { serverTime: string }>(
  load: () => Promise<T>,
  intervalMs: number,
  enabled = true,
) {
  const [state, setState] = useState<PollState<T>>({
    data: null,
    error: null,
    offsetMs: 0,
  });
  const [tick, setTick] = useState(0);

  const accept = useCallback((data: T) => {
    const server = Date.parse(data.serverTime);
    setState({
      data,
      error: null,
      offsetMs: Number.isNaN(server) ? 0 : server - Date.now(),
    });
  }, []);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    load()
      .then((data) => {
        if (!cancelled) accept(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState((s) => ({
            ...s,
            error: err instanceof Error ? err.message : "Connection problem.",
          }));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [load, tick, enabled, accept]);

  useEffect(() => {
    if (!enabled) return;
    const id = setInterval(() => {
      if (document.visibilityState === "visible") setTick((t) => t + 1);
    }, intervalMs);
    const resume = () => {
      if (document.visibilityState === "visible") setTick((t) => t + 1);
    };
    document.addEventListener("visibilitychange", resume);
    return () => {
      clearInterval(id);
      document.removeEventListener("visibilitychange", resume);
    };
  }, [intervalMs, enabled]);

  return {
    data: state.data,
    error: state.error,
    offsetMs: state.offsetMs,
    refresh: () => setTick((t) => t + 1),
    accept,
  };
}

/** Milliseconds left until an ISO deadline, using the server clock offset. */
export function remainingMs(deadline: string | null, nowMs: number, offsetMs: number): number {
  if (!deadline) return 0;
  const end = Date.parse(deadline);
  if (Number.isNaN(end)) return 0;
  return Math.max(0, end - (nowMs + offsetMs));
}

export function formatSeconds(ms: number): string {
  const total = Math.ceil(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return m > 0 ? `${m}:${String(s).padStart(2, "0")}` : `${s}s`;
}

/** Re-renders every `stepMs` while `active`, for countdowns. */
export function useNow(active: boolean, stepMs = 250): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => setNow(Date.now()), stepMs);
    return () => clearInterval(id);
  }, [active, stepMs]);
  return now;
}
