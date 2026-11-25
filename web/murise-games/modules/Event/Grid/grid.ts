/**
 * Rhythm Grid Hunt turn tracker (GM-049): pure rules, no React.
 *
 * Decided 25 Nov 2026: one shared 3×3 grid, 24-point maximum per match.
 * 4 rounds × 2 turns. Each turn: 1 hunter (centre) vs 3 runners (corners),
 * 12 movement cues. The turn ends after 12 cues or when all 3 runners are
 * caught. Hunting team scores the catches, running team the survivors
 * (3 − catches). Each member hunts once and sits out one running turn.
 * Points = 5 × raw ÷ 24.
 */

export type Side = "A" | "B";
export type GridAction = "cue" | "catch";

export const RUNNERS = 3;

export interface GridTurn {
  hunter: Side;
  cues: number;
  catches: number;
  done: boolean;
  /** Ended by the judge before 12 cues (e.g. stopped for safety). */
  endedEarly: boolean;
  history: GridAction[];
}

export interface GridMatchState {
  version: 1;
  fixtureId: string;
  cuesPerTurn: number;
  turns: GridTurn[];
  current: number;
}

export function newGridMatch(fixtureId: string, turnCount = 8, cuesPerTurn = 12): GridMatchState {
  return {
    version: 1,
    fixtureId,
    cuesPerTurn,
    current: 0,
    // In each round the first turn is hunted by team A, the second by team B.
    turns: Array.from({ length: turnCount }, (_, i) => ({
      hunter: i % 2 === 0 ? "A" : "B",
      cues: 0,
      catches: 0,
      done: false,
      endedEarly: false,
      history: [],
    })),
  };
}

function withTurn(state: GridMatchState, turn: GridTurn): GridMatchState {
  return { ...state, turns: state.turns.map((t, i) => (i === state.current ? turn : t)) };
}

/** One movement cue was given. The 12th cue ends the turn. */
export function cue(state: GridMatchState): GridMatchState {
  const turn = state.turns[state.current];
  if (!turn || turn.done || turn.cues >= state.cuesPerTurn) return state;
  const cues = turn.cues + 1;
  return withTurn(state, {
    ...turn,
    cues,
    done: cues >= state.cuesPerTurn,
    history: [...turn.history, "cue"],
  });
}

/** A runner landed on the hunter's square. The 3rd catch ends the turn. */
export function addCatch(state: GridMatchState): GridMatchState {
  const turn = state.turns[state.current];
  if (!turn || turn.done || turn.catches >= RUNNERS) return state;
  const catches = turn.catches + 1;
  return withTurn(state, {
    ...turn,
    catches,
    done: catches >= RUNNERS,
    history: [...turn.history, "catch"],
  });
}

/** Disputed cue: restore the last agreed positions and give the cue again. */
export function replayCue(state: GridMatchState): GridMatchState {
  const turn = state.turns[state.current];
  if (!turn || turn.history[turn.history.length - 1] !== "cue") return state;
  return withTurn(state, {
    ...turn,
    cues: turn.cues - 1,
    done: false,
    history: turn.history.slice(0, -1),
  });
}

/** Stops the turn now (safety, or the host decides); unplayed cues don't matter. */
export function endTurn(state: GridMatchState): GridMatchState {
  const turn = state.turns[state.current];
  if (!turn || turn.done) return state;
  return withTurn(state, { ...turn, done: true, endedEarly: true });
}

/** Removes the last cue or catch (and reopens the turn), or goes back a turn. */
export function undo(state: GridMatchState): GridMatchState {
  const turn = state.turns[state.current];
  if (!turn) return state;
  if (turn.endedEarly) {
    return withTurn(state, { ...turn, done: false, endedEarly: false });
  }
  const last = turn.history[turn.history.length - 1];
  if (!last) {
    return state.current > 0 ? { ...state, current: state.current - 1 } : state;
  }
  return withTurn(state, {
    ...turn,
    cues: last === "cue" ? turn.cues - 1 : turn.cues,
    catches: last === "catch" ? turn.catches - 1 : turn.catches,
    done: false,
    history: turn.history.slice(0, -1),
  });
}

export function nextTurn(state: GridMatchState): GridMatchState {
  const turn = state.turns[state.current];
  if (!turn || !turn.done || state.current >= state.turns.length - 1) return state;
  return { ...state, current: state.current + 1 };
}

export function isFinished(state: GridMatchState): boolean {
  return state.turns.every((t) => t.done);
}

/** Official values per turn: catches for the hunting team, survivors for the other. */
export function teamValues(state: GridMatchState): Record<Side, number[]> {
  return {
    A: state.turns.map((t) => (t.hunter === "A" ? t.catches : RUNNERS - t.catches)),
    B: state.turns.map((t) => (t.hunter === "B" ? t.catches : RUNNERS - t.catches)),
  };
}

export function totals(state: GridMatchState): Record<Side, number> {
  const values = teamValues(state);
  const sum = (list: number[], upTo: number) =>
    list.slice(0, upTo).reduce((a, b) => a + b, 0);
  // Count only finished turns, so the live score doesn't jump ahead.
  const played = state.turns.filter((t) => t.done).length;
  return { A: sum(values.A, played), B: sum(values.B, played) };
}

export function roundOf(turnIndex: number): number {
  return Math.floor(turnIndex / 2);
}

/**
 * Who hunts for a team in a round, and who sits out its running turn in that
 * round: the same person, so over 4 rounds each member hunts once and rests once.
 */
export function hunterFor<T>(players: T[], round: number): T | null {
  return players.length === 0 ? null : players[round % players.length];
}

export function runnersFor<T>(players: T[], round: number): T[] {
  if (players.length <= RUNNERS) return players;
  const out = round % players.length;
  return players.filter((_, i) => i !== out).slice(0, RUNNERS);
}

// ---- Keeping the match across a refresh (this device only) ----

const storageKey = (fixtureId: string) => `murise:grid:${fixtureId}`;

export function loadGridMatch(fixtureId: string): GridMatchState | null {
  try {
    const raw = window.localStorage.getItem(storageKey(fixtureId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as GridMatchState;
    return parsed?.version === 1 && parsed.fixtureId === fixtureId ? parsed : null;
  } catch {
    return null;
  }
}

export function storeGridMatch(state: GridMatchState) {
  try {
    window.localStorage.setItem(storageKey(state.fixtureId), JSON.stringify(state));
  } catch {
    // The tracker still works; it just won't survive a refresh.
  }
}

export function clearGridMatch(fixtureId: string) {
  try {
    window.localStorage.removeItem(storageKey(fixtureId));
  } catch {
    // Nothing to clear.
  }
}

/** Rhythm Grid style games: head-to-head, turns scored 0–3 that total 3. */
export function isGridGame(game: {
  headToHead: boolean;
  maximumPerField: number;
  fields: string[];
}): boolean {
  return (
    game.headToHead &&
    game.maximumPerField === RUNNERS &&
    game.fields.length > 0 &&
    game.fields.length % 2 === 0 &&
    game.fields.every((f) => /turn/i.test(f))
  );
}
