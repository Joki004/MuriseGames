/**
 * The Sensory Chain turn tracker (GM-045): pure rules, no React.
 *
 * 4 turns of 45 s, 3 action cards per turn (printed triplets), 1 pass per turn.
 * Roles rotate every turn: Mime → Sound FX → Guesser → Card/Time helper.
 * A correct card scores 1. A pass, a voided card (spoken clue, peeking, help from
 * the helper) or a card left when time runs out scores 0. Max 12 → 5 × raw ÷ 12.
 */

export type CardOutcome = "correct" | "pass" | "invalid" | "time";

export const ROLES = ["Mime", "Sound FX", "Guesser", "Card / time helper"] as const;

export interface TurnState {
  outcomes: CardOutcome[];
  /** Epoch ms when the 45 s end, while running. */
  endsAt: number | null;
  /** Ms left while paused (or before the first start). */
  remainingMs: number;
  started: boolean;
}

export interface ChainMatchState {
  version: 1;
  fixtureId: string;
  cardsPerTurn: number;
  turnMs: number;
  turns: TurnState[];
  current: number;
  /** Shifts the role rotation if the players start in another order. */
  rotation: number;
}

export function newChainMatch(
  fixtureId: string,
  turnCount = 4,
  cardsPerTurn = 3,
  turnSeconds = 45,
): ChainMatchState {
  return {
    version: 1,
    fixtureId,
    cardsPerTurn,
    turnMs: turnSeconds * 1000,
    current: 0,
    rotation: 0,
    turns: Array.from({ length: turnCount }, () => ({
      outcomes: [],
      endsAt: null,
      remainingMs: turnSeconds * 1000,
      started: false,
    })),
  };
}

export function remainingMs(turn: TurnState, now: number): number {
  return turn.endsAt === null ? turn.remainingMs : Math.max(0, turn.endsAt - now);
}

export function isTurnOver(state: ChainMatchState, index: number): boolean {
  const turn = state.turns[index];
  return turn !== undefined && turn.outcomes.length >= state.cardsPerTurn;
}

export function passUsed(turn: TurnState): boolean {
  return turn.outcomes.includes("pass");
}

export function turnScore(turn: TurnState): number {
  return turn.outcomes.filter((o) => o === "correct").length;
}

export function isFinished(state: ChainMatchState): boolean {
  return state.turns.every((_, i) => isTurnOver(state, i));
}

export function totalScore(state: ChainMatchState): number {
  return state.turns.reduce((sum, t) => sum + turnScore(t), 0);
}

/** Official values, Turn 1..4: correct cards per turn. */
export function turnValues(state: ChainMatchState): number[] {
  return state.turns.map(turnScore);
}

export function startTimer(state: ChainMatchState, now: number): ChainMatchState {
  const turn = state.turns[state.current];
  if (!turn || turn.endsAt !== null || isTurnOver(state, state.current)) return state;
  return replaceTurn(state, state.current, {
    ...turn,
    started: true,
    endsAt: now + turn.remainingMs,
  });
}

export function pauseTimer(state: ChainMatchState, now: number): ChainMatchState {
  const turn = state.turns[state.current];
  if (!turn || turn.endsAt === null) return state;
  return replaceTurn(state, state.current, {
    ...turn,
    endsAt: null,
    remainingMs: Math.max(0, turn.endsAt - now),
  });
}

/** Records the current card. One pass per turn; nothing after the turn is over. */
export function recordCard(
  state: ChainMatchState,
  outcome: Exclude<CardOutcome, "time">,
  now: number,
): ChainMatchState {
  const turn = state.turns[state.current];
  if (!turn || isTurnOver(state, state.current)) return state;
  if (outcome === "pass" && passUsed(turn)) return state;

  const outcomes = [...turn.outcomes, outcome];
  const done = outcomes.length >= state.cardsPerTurn;
  return replaceTurn(state, state.current, {
    ...turn,
    outcomes,
    // The triplet is finished: stop the clock where it is.
    endsAt: done ? null : turn.endsAt,
    remainingMs: done ? remainingMs(turn, now) : turn.remainingMs,
  });
}

/** Time is up: every card not played yet scores 0. */
export function expireTurn(state: ChainMatchState): ChainMatchState {
  const turn = state.turns[state.current];
  if (!turn || isTurnOver(state, state.current)) return state;
  const missing = state.cardsPerTurn - turn.outcomes.length;
  return replaceTurn(state, state.current, {
    ...turn,
    outcomes: [...turn.outcomes, ...Array<CardOutcome>(missing).fill("time")],
    endsAt: null,
    remainingMs: 0,
  });
}

/** Removes the last card of the current turn ("time" cards go back all at once). */
export function undoCard(state: ChainMatchState): ChainMatchState {
  const turn = state.turns[state.current];
  if (!turn) return state;
  if (turn.outcomes.length === 0) {
    return state.current > 0 ? { ...state, current: state.current - 1 } : state;
  }
  const last = turn.outcomes[turn.outcomes.length - 1];
  const outcomes =
    last === "time"
      ? turn.outcomes.filter((o) => o !== "time")
      : turn.outcomes.slice(0, -1);
  return replaceTurn(state, state.current, {
    ...turn,
    outcomes,
    endsAt: null,
    remainingMs: last === "time" ? 0 : turn.remainingMs,
  });
}

export function nextTurn(state: ChainMatchState): ChainMatchState {
  if (!isTurnOver(state, state.current) || state.current >= state.turns.length - 1) {
    return state;
  }
  return { ...state, current: state.current + 1 };
}

export function rotate(state: ChainMatchState): ChainMatchState {
  return { ...state, rotation: state.rotation + 1 };
}

/**
 * Who plays which role in a turn. Turn by turn everyone moves one role along,
 * so each player is Mime, Sound FX, Guesser and helper once in 4 turns.
 * With fewer than 4 players the host is the helper.
 */
export function rolesFor<T>(players: T[], turnIndex: number, rotation: number): (T | null)[] {
  const n = players.length;
  return ROLES.map((_, role) => {
    if (n === 0) return null;
    if (role >= n) return null;
    const index = (((role + turnIndex + rotation) % n) + n) % n;
    return players[index];
  });
}

function replaceTurn(state: ChainMatchState, index: number, turn: TurnState): ChainMatchState {
  return { ...state, turns: state.turns.map((t, i) => (i === index ? turn : t)) };
}

// ---- Keeping the match across a refresh (this device only) ----

const storageKey = (fixtureId: string) => `murise:chain:${fixtureId}`;

export function loadChainMatch(fixtureId: string): ChainMatchState | null {
  try {
    const raw = window.localStorage.getItem(storageKey(fixtureId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ChainMatchState;
    return parsed?.version === 1 && parsed.fixtureId === fixtureId ? parsed : null;
  } catch {
    return null;
  }
}

export function storeChainMatch(state: ChainMatchState) {
  try {
    window.localStorage.setItem(storageKey(state.fixtureId), JSON.stringify(state));
  } catch {
    // The tracker still works; it just won't survive a refresh.
  }
}

export function clearChainMatch(fixtureId: string) {
  try {
    window.localStorage.removeItem(storageKey(fixtureId));
  } catch {
    // Nothing to clear.
  }
}

/** Sensory Chain style games: one team, "Turn 1..n", up to 3 cards per turn. */
export function isChainGame(game: {
  headToHead: boolean;
  maximumPerField: number;
  fields: string[];
}): boolean {
  return (
    !game.headToHead &&
    game.maximumPerField === 3 &&
    game.fields.length > 0 &&
    game.fields.every((f) => /^Turn \d+$/.test(f))
  );
}
