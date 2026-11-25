/**
 * Grab the Cup duel tracker (GM-044): pure rules, no React.
 *
 * 4 duels per match, 4 different players per team (repeats rotate fairly when a
 * team is smaller). Each duel: first to 2 valid attempts. A false start gives
 * the attempt to the other player. A disputed grab (both hands, dropped cup,
 * moved table) is replayed and scores nothing.
 */

export type Side = "A" | "B";

export type AttemptEvent =
  | { kind: "win"; side: Side }
  | { kind: "falseStart"; side: Side }
  | { kind: "replay" };

export interface DuelState {
  /** participantId, or null when the players list could not be loaded. */
  playerA: string | null;
  playerB: string | null;
  events: AttemptEvent[];
}

export interface DuelMatchState {
  version: 1;
  fixtureId: string;
  duels: DuelState[];
  current: number;
}

export const ATTEMPTS_TO_WIN = 2;

/** Who won the attempt an event records (a false start goes to the other side). */
export function attemptWinner(event: AttemptEvent): Side | null {
  if (event.kind === "win") return event.side;
  if (event.kind === "falseStart") return event.side === "A" ? "B" : "A";
  return null;
}

export function attemptScore(duel: DuelState): Record<Side, number> {
  const score: Record<Side, number> = { A: 0, B: 0 };
  for (const event of duel.events) {
    const winner = attemptWinner(event);
    if (winner) score[winner]++;
  }
  return score;
}

export function duelWinner(duel: DuelState): Side | null {
  const score = attemptScore(duel);
  if (score.A >= ATTEMPTS_TO_WIN) return "A";
  if (score.B >= ATTEMPTS_TO_WIN) return "B";
  return null;
}

export function replayCount(duel: DuelState): number {
  return duel.events.filter((e) => e.kind === "replay").length;
}

/** Player for duel i: each player once in order, then repeats from the top. */
export function rotationPick<T>(players: T[], duelIndex: number): T | null {
  if (players.length === 0) return null;
  return players[duelIndex % players.length];
}

export function newMatch(
  fixtureId: string,
  duelCount: number,
  playersA: string[],
  playersB: string[],
): DuelMatchState {
  return {
    version: 1,
    fixtureId,
    current: 0,
    duels: Array.from({ length: duelCount }, (_, i) => ({
      playerA: rotationPick(playersA, i),
      playerB: rotationPick(playersB, i),
      events: [],
    })),
  };
}

/** Adds an attempt to the current duel; ignored once the duel is decided. */
export function addEvent(state: DuelMatchState, event: AttemptEvent): DuelMatchState {
  const duel = state.duels[state.current];
  if (!duel || duelWinner(duel) !== null) return state;
  return replaceDuel(state, state.current, { ...duel, events: [...duel.events, event] });
}

/** Removes the last attempt of the current duel, or goes back to the previous duel. */
export function undo(state: DuelMatchState): DuelMatchState {
  const duel = state.duels[state.current];
  if (duel && duel.events.length > 0) {
    return replaceDuel(state, state.current, { ...duel, events: duel.events.slice(0, -1) });
  }
  if (state.current > 0) {
    return { ...state, current: state.current - 1 };
  }
  return state;
}

export function nextDuel(state: DuelMatchState): DuelMatchState {
  const duel = state.duels[state.current];
  if (!duel || duelWinner(duel) === null || state.current >= state.duels.length - 1) {
    return state;
  }
  return { ...state, current: state.current + 1 };
}

export function setPlayer(
  state: DuelMatchState,
  duelIndex: number,
  side: Side,
  participantId: string | null,
): DuelMatchState {
  const duel = state.duels[duelIndex];
  if (!duel) return state;
  return replaceDuel(
    state,
    duelIndex,
    side === "A" ? { ...duel, playerA: participantId } : { ...duel, playerB: participantId },
  );
}

export function isFinished(state: DuelMatchState): boolean {
  return state.duels.every((d) => duelWinner(d) !== null);
}

export function duelsWon(state: DuelMatchState): Record<Side, number> {
  const won: Record<Side, number> = { A: 0, B: 0 };
  for (const duel of state.duels) {
    const winner = duelWinner(duel);
    if (winner) won[winner]++;
  }
  return won;
}

/** Official values: 1 for each duel a team won, 0 otherwise (same order as Duel 1..4). */
export function teamValues(state: DuelMatchState): Record<Side, number[]> {
  return {
    A: state.duels.map((d) => (duelWinner(d) === "A" ? 1 : 0)),
    B: state.duels.map((d) => (duelWinner(d) === "B" ? 1 : 0)),
  };
}

/** Star-player credits: the winner of each duel, when known. */
export function winnerCredits(
  state: DuelMatchState,
): { segmentIndex: number; participantId: string }[] {
  return state.duels.flatMap((duel, segmentIndex) => {
    const winner = duelWinner(duel);
    const participantId = winner === "A" ? duel.playerA : winner === "B" ? duel.playerB : null;
    return participantId ? [{ segmentIndex, participantId }] : [];
  });
}

function replaceDuel(state: DuelMatchState, index: number, duel: DuelState): DuelMatchState {
  return { ...state, duels: state.duels.map((d, i) => (i === index ? duel : d)) };
}

// ---- Keeping the match across a refresh (this device only) ----

const storageKey = (fixtureId: string) => `murise:duel:${fixtureId}`;

export function loadMatch(fixtureId: string): DuelMatchState | null {
  try {
    const raw = window.localStorage.getItem(storageKey(fixtureId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as DuelMatchState;
    return parsed?.version === 1 && parsed.fixtureId === fixtureId ? parsed : null;
  } catch {
    return null;
  }
}

export function storeMatch(state: DuelMatchState) {
  try {
    window.localStorage.setItem(storageKey(state.fixtureId), JSON.stringify(state));
  } catch {
    // Private mode or full storage: the match still works, it just won't survive a refresh.
  }
}

export function clearMatch(fixtureId: string) {
  try {
    window.localStorage.removeItem(storageKey(fixtureId));
  } catch {
    // Nothing to clear.
  }
}

/** Grab the Cup style games: head-to-head, one point per duel, fields "Duel 1..n". */
export function isDuelGame(game: {
  headToHead: boolean;
  maximumPerField: number;
  fields: string[];
}): boolean {
  return (
    game.headToHead &&
    game.maximumPerField === 1 &&
    game.fields.length > 0 &&
    game.fields.every((f) => /^Duel \d+$/.test(f))
  );
}
