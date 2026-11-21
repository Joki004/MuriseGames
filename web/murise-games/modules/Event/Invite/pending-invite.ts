const STORAGE_KEY = "murise.pending-invite";
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;
const RETENTION_MS = 30 * 60 * 1000;

interface StoredInvite {
  token: string;
  savedAt: number;
}

export function capturePendingInvite(): string | null {
  const fragment = new URLSearchParams(window.location.hash.slice(1));

  if (fragment.has("token")) {
    const token = fragment.get("token") ?? "";

    try {
      sessionStorage.removeItem(STORAGE_KEY);

      if (!TOKEN_PATTERN.test(token)) return null;

      sessionStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ token, savedAt: Date.now() }),
      );

      window.history.replaceState(
        window.history.state,
        "",
        window.location.pathname + window.location.search,
      );
    } catch {
      // Keep a valid fragment token usable on this page if storage is blocked.
      // Returning after authentication may require reopening the invite link.
    }

    return TOKEN_PATTERN.test(token) ? token : null;
  }

  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;

    const stored = JSON.parse(raw) as Partial<StoredInvite>;

    if (
      typeof stored.token !== "string" ||
      !TOKEN_PATTERN.test(stored.token) ||
      typeof stored.savedAt !== "number" ||
      !Number.isFinite(stored.savedAt) ||
      stored.savedAt > Date.now() ||
      Date.now() - stored.savedAt > RETENTION_MS
    ) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }

    return stored.token;
  } catch {
    return null;
  }
}

export function clearPendingInvite(token: string): void {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return;

    const stored = JSON.parse(raw) as Partial<StoredInvite>;

    // Do not clear a newer invitation opened while a request was running.
    if (stored.token === token) {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // No action needed if browser storage is unavailable.
  }
}