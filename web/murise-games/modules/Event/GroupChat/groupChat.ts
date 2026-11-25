/**
 * Group Chat Meltdown round tracker (GM-050): pure rules, no React.
 *
 * Decided 25 Nov 2026 (slide 12): 4 rounds, a different creator each round.
 * The creator privately gets a situation and has 60 s to pick 2–4 memes/GIFs
 * on their own phone; teammates then have 45 s to guess the situation.
 * Guessed = 1 point, not guessed or time up = 0. Max 4 → 5 × raw ÷ 4.
 */

export type RoundPhase = "select" | "guess" | "done";

export interface ChatRound {
  phase: RoundPhase;
  /** Epoch ms when the current phase timer ends, while running. */
  endsAt: number | null;
  /** Ms left in the current phase while paused (or before starting). */
  remainingMs: number;
  started: boolean;
  /** 1 guessed, 0 not guessed / time up, null while playing. */
  point: 0 | 1 | null;
  timedOut: boolean;
  /** Guess time left when the round ended, to restore it on undo. */
  guessLeftMs: number;
}

export interface ChatMatchState {
  version: 1;
  fixtureId: string;
  selectMs: number;
  guessMs: number;
  rounds: ChatRound[];
  current: number;
  /** Shifts who creates first if the team starts in another order. */
  rotation: number;
}

function freshRound(selectMs: number, guessMs: number): ChatRound {
  return {
    phase: "select",
    endsAt: null,
    remainingMs: selectMs,
    started: false,
    point: null,
    timedOut: false,
    guessLeftMs: guessMs,
  };
}

export function newChatMatch(
  fixtureId: string,
  roundCount = 4,
  selectSeconds = 60,
  guessSeconds = 45,
): ChatMatchState {
  const selectMs = selectSeconds * 1000;
  const guessMs = guessSeconds * 1000;
  return {
    version: 1,
    fixtureId,
    selectMs,
    guessMs,
    current: 0,
    rotation: 0,
    rounds: Array.from({ length: roundCount }, () => freshRound(selectMs, guessMs)),
  };
}

export function remainingMs(round: ChatRound, now: number): number {
  return round.endsAt === null ? round.remainingMs : Math.max(0, round.endsAt - now);
}

export function isRunning(round: ChatRound): boolean {
  return round.endsAt !== null;
}

/** Starts or resumes the clock of the current phase. */
export function startClock(state: ChatMatchState, now: number): ChatMatchState {
  const round = state.rounds[state.current];
  if (!round || round.phase === "done" || round.endsAt !== null) return state;
  return replaceRound(state, state.current, {
    ...round,
    started: true,
    endsAt: now + round.remainingMs,
  });
}

export function pauseClock(state: ChatMatchState, now: number): ChatMatchState {
  const round = state.rounds[state.current];
  if (!round || round.endsAt === null) return state;
  return replaceRound(state, state.current, {
    ...round,
    endsAt: null,
    remainingMs: Math.max(0, round.endsAt - now),
  });
}

/** Images are ready: the 45 s guessing starts now. */
export function startGuessing(state: ChatMatchState, now: number): ChatMatchState {
  const round = state.rounds[state.current];
  if (!round || round.phase !== "select") return state;
  return replaceRound(state, state.current, {
    ...round,
    phase: "guess",
    started: true,
    endsAt: now + state.guessMs,
    remainingMs: state.guessMs,
  });
}

export function markGuess(state: ChatMatchState, guessed: boolean, now: number): ChatMatchState {
  const round = state.rounds[state.current];
  if (!round || round.phase !== "guess") return state;
  return replaceRound(state, state.current, {
    ...round,
    phase: "done",
    point: guessed ? 1 : 0,
    timedOut: false,
    guessLeftMs: remainingMs(round, now),
    endsAt: null,
    remainingMs: 0,
  });
}

/** The 45 s ran out without a correct guess. */
export function expireGuess(state: ChatMatchState): ChatMatchState {
  const round = state.rounds[state.current];
  if (!round || round.phase !== "guess") return state;
  return replaceRound(state, state.current, {
    ...round,
    phase: "done",
    point: 0,
    timedOut: true,
    guessLeftMs: 0,
    endsAt: null,
    remainingMs: 0,
  });
}

/** Steps back: result → guessing (paused) → choosing → previous round. */
export function undo(state: ChatMatchState): ChatMatchState {
  const round = state.rounds[state.current];
  if (!round) return state;
  if (round.phase === "done") {
    return replaceRound(state, state.current, {
      ...round,
      phase: "guess",
      point: null,
      timedOut: false,
      endsAt: null,
      remainingMs: round.guessLeftMs,
    });
  }
  if (round.phase === "guess") {
    return replaceRound(state, state.current, freshRound(state.selectMs, state.guessMs));
  }
  if (!round.started && state.current > 0) {
    return { ...state, current: state.current - 1 };
  }
  return replaceRound(state, state.current, freshRound(state.selectMs, state.guessMs));
}

export function nextRound(state: ChatMatchState): ChatMatchState {
  const round = state.rounds[state.current];
  if (!round || round.phase !== "done" || state.current >= state.rounds.length - 1) {
    return state;
  }
  return { ...state, current: state.current + 1 };
}

export function rotate(state: ChatMatchState): ChatMatchState {
  return { ...state, rotation: state.rotation + 1 };
}

export function isFinished(state: ChatMatchState): boolean {
  return state.rounds.every((r) => r.phase === "done");
}

/** Official values, Round 1..4: 1 if guessed. */
export function roundValues(state: ChatMatchState): number[] {
  return state.rounds.map((r) => r.point ?? 0);
}

export function totalPoints(state: ChatMatchState): number {
  return roundValues(state).reduce((a, b) => a + b, 0);
}

/** A different creator each round, in team order, then from the top again. */
export function creatorFor<T>(players: T[], roundIndex: number, rotation: number): T | null {
  const n = players.length;
  if (n === 0) return null;
  return players[(((roundIndex + rotation) % n) + n) % n];
}

function replaceRound(state: ChatMatchState, index: number, round: ChatRound): ChatMatchState {
  return { ...state, rounds: state.rounds.map((r, i) => (i === index ? round : r)) };
}

// ---- Keeping the match across a refresh (this device only) ----

const storageKey = (fixtureId: string) => `murise:groupchat:${fixtureId}`;

export function loadChatMatch(fixtureId: string): ChatMatchState | null {
  try {
    const raw = window.localStorage.getItem(storageKey(fixtureId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ChatMatchState;
    return parsed?.version === 1 && parsed.fixtureId === fixtureId ? parsed : null;
  } catch {
    return null;
  }
}

export function storeChatMatch(state: ChatMatchState) {
  try {
    window.localStorage.setItem(storageKey(state.fixtureId), JSON.stringify(state));
  } catch {
    // The tracker still works; it just won't survive a refresh.
  }
}

export function clearChatMatch(fixtureId: string) {
  try {
    window.localStorage.removeItem(storageKey(fixtureId));
  } catch {
    // Nothing to clear.
  }
}

/** Group Chat style games: one team, "Round 1..n", 1 point per round. */
export function isGroupChatGame(game: {
  headToHead: boolean;
  maximumPerField: number;
  fields: string[];
}): boolean {
  return (
    !game.headToHead &&
    game.maximumPerField === 1 &&
    game.fields.length > 0 &&
    game.fields.every((f) => /^Round \d+$/.test(f))
  );
}
