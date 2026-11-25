"use client";

import { useCallback, useEffect, useState } from "react";
import { RosterController } from "./roster.controller";
import type { EventRosterResponse } from "./roster.type";

/** Sent by PictureControl whenever someone changes a picture. */
const PICTURE_CHANGED = "murise-picture-changed";

/** Image URLs live 15 minutes on the server; refresh well before. */
const REFRESH_MS = 5 * 60 * 1000;

/**
 * Teams, members and pictures for screens. Refreshes every 5 minutes, when the
 * tab becomes visible again, when a picture changes in this browser, and when
 * `refreshKey` changes (pass something that changes when teams change).
 * State is only set inside callbacks.
 */
export function useEventRoster(
  eventId: string | undefined,
  refreshKey?: string | number,
) {
  const [roster, setRoster] = useState<EventRosterResponse | null>(null);
  const [tick, setTick] = useState(0);

  const refresh = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    if (!eventId) return;
    let cancelled = false;
    RosterController.get(eventId)
      .then((data) => {
        if (!cancelled) setRoster(data);
      })
      .catch(() => {
        // Keep the last roster: screens fall back to names and colors.
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, tick, refreshKey]);

  useEffect(() => {
    const id = setInterval(() => {
      if (document.visibilityState === "visible") refresh();
    }, REFRESH_MS);

    function onVisible() {
      if (document.visibilityState === "visible") refresh();
    }

    window.addEventListener(PICTURE_CHANGED, refresh);
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      clearInterval(id);
      window.removeEventListener(PICTURE_CHANGED, refresh);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [refresh]);

  return { roster, refresh };
}
