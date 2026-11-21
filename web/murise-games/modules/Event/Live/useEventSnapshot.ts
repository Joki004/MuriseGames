"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { extractApiErrorMessage } from "@/modules/Api/api.service";
import { EventLiveController } from "./live.controller";
import type { EventSnapshotResponse } from "./live.type";

const BASE_POLL_MS = 2000;
const MAX_BACKOFF_MS = 16000;

export function useEventSnapshot(eventId: string, active = true) {
  const [snapshot, setSnapshot] = useState<EventSnapshotResponse | null>(null);
  const [clockOffsetMs, setClockOffsetMs] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const errorCountRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);

  const applySnapshot = useCallback((next: EventSnapshotResponse) => {
    const serverMs = Date.parse(next.serverTime);
    if (!Number.isNaN(serverMs)) {
      setClockOffsetMs(serverMs - Date.now());
    }
    setSnapshot(next);
    setError(null);
    errorCountRef.current = 0;
  }, []);

  const clearScheduled = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const fetchOnce = useCallback(async () => {
    try {
      const data = await EventLiveController.snapshot(eventId);
      if (!mountedRef.current) return;
      applySnapshot(data);
    } catch (err: unknown) {
      if (!mountedRef.current) return;
      errorCountRef.current += 1;
      setError(extractApiErrorMessage(err, "Could not sync live event state."));
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, [eventId, applySnapshot]);

  useEffect(() => {
    mountedRef.current = true;
    if (!active) return;

    let cancelled = false;

    const scheduleNext = () => {
      if (cancelled || document.visibilityState === "hidden") return;
      const delay = Math.min(
        BASE_POLL_MS * Math.pow(2, errorCountRef.current),
        MAX_BACKOFF_MS
      );
      timerRef.current = setTimeout(() => {
        void pollLoop();
      }, delay);
    };

    const pollLoop = async () => {
      clearScheduled();
      if (cancelled || document.visibilityState === "hidden") return;
      await fetchOnce();
      scheduleNext();
    };

    const handleResume = () => {
      if (document.visibilityState === "visible") {
        void pollLoop();
      } else {
        clearScheduled();
      }
    };

    void pollLoop();
    document.addEventListener("visibilitychange", handleResume);
    window.addEventListener("online", handleResume);

    return () => {
      cancelled = true;
      mountedRef.current = false;
      clearScheduled();
      document.removeEventListener("visibilitychange", handleResume);
      window.removeEventListener("online", handleResume);
    };
  }, [active, fetchOnce, clearScheduled]);

  return {
    snapshot,
    clockOffsetMs,
    loading,
    error,
    applySnapshot,
    refetch: fetchOnce,
  };
}