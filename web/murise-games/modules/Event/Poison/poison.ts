/**
 * Poison Game round tracker (GM-048): pure rules, no React.
 *
 * Decided 25 Nov 2026: 6 identical cups, 2 rounds, the other team starts round 2.
 * Teams alternate, a different teammate each turn, 5 s to choose. No choice in
 * time: the host picks a random remaining cup for them. A safe cup leaves the
 * table; the poison ends the round and the other team wins it. Marker seen by
 * accident: reshuffle and replay the round. Points = 5 × rounds won ÷ 2.
 *
 * The poison cup is either a physical marker (the judge says safe/poison) or
 * chosen secretly by this phone. It never leaves this device.
 */

export type Side = "A" | "B";
export type PickResult = "safe" | "poison";

export interface Pick {
  cup: number;
  side: Side;
  result: PickResult;
  /** The host picked it because the player didn't choose in 5 s. */
  random: boolean;
}

export interface PoisonRound {
  starter: Side;
  started: boolean;
  /** Set when this phone chose the poison secretly; null with a physical marker. */
  secretCup: number | null;
  picks: Pick[];
  replays: number;
  /** Epoch ms when the current 5 s choice ends, while running. */
  turnEndsAt: number | null;
}

export interface PoisonMatchState {
  version: 1;
  fixtureId: string;
  cups: number;
  turnMs: number;
  rounds: PoisonRound[];
  current: number;
}

const other = (side: Side): Side => (side === "A" ? "B" : "A");

function freshRound(starter: Side): PoisonRound {
  return { starter, started: false, secretCup: null, picks: [], replays: 0, turnEndsAt: null };
}

export function newPoisonMatch(
  fixtureId: string,
  roundCount = 2,
  cups = 6,
  turnSeconds = 5,
): PoisonMatchState {
  return {
    version: 1,
    fixtureId,
    cups,
    turnMs: turnSeconds * 1000,
    current: 0,
    rounds: Array.from({ length: roundCount }, (_, i) => freshRound(i % 2 === 0 ? "A" : "B")),
  };
}

export function loser(round: PoisonRound): Side | null {
  return round.picks.find((p) => p.result === "poison")?.side ?? null;
}

export function roundWinner(round: PoisonRound): Side | null {
  const lost = loser(round);
  return lost ? other(lost) : null;
}

export function isRoundOver(round: PoisonRound): boolean {
  return loser(round) !== null;
}

/** Whose turn it is now (teams alternate from the starter). */
export function turnSide(round: PoisonRound): Side {
  return round.picks.length % 2 === 0 ? round.starter : other(round.starter);
}

export function remainingCups(state: PoisonMatchState, round: PoisonRound): number[] {
  const taken = new Set(round.picks.map((p) => p.cup));
  return Array.from({ length: state.cups }, (_, i) => i + 1).filter((c) => !taken.has(c));
}

/**
 * Which teammate chooses: each team's players in order, one per turn, carried
 * over from round to round so everyone gets a go.
 */
export function chooserIndex(state: PoisonMatchState, side: Side): number {
  let count = 0;
  state.rounds.forEach((round, i) => {
    if (i > state.current) return;
    count += round.picks.filter((p) => p.side === side).length;
  });
  return count;
}

/** secretCup: a cup number chosen by this phone, or null for a physical marker. */
export function startRound(
  state: PoisonMatchState,
  now: number,
  secretCup: number | null,
): PoisonMatchState {
  const round = state.rounds[state.current];
  if (!round || round.started) return state;
  return replaceRound(state, state.current, {
    ...round,
    started: true,
    secretCup,
    turnEndsAt: now + state.turnMs,
  });
}

/**
 * Records the chosen cup. With a secret set on this phone the result is known;
 * with a physical marker the judge gives it.
 */
export function pick(
  state: PoisonMatchState,
  cup: number,
  judged: PickResult | null,
  now: number,
  random = false,
): PoisonMatchState {
  const round = state.rounds[state.current];
  if (!round || !round.started || isRoundOver(round)) return state;
  if (!remainingCups(state, round).includes(cup)) return state;

  const result: PickResult | null =
    round.secretCup !== null ? (cup === round.secretCup ? "poison" : "safe") : judged;
  if (result === null) return state;

  const picks = [...round.picks, { cup, side: turnSide(round), result, random }];
  return replaceRound(state, state.current, {
    ...round,
    picks,
    turnEndsAt: result === "poison" ? null : now + state.turnMs,
  });
}

/** Restarts the 5 s for the current chooser (e.g. after a pause in the room). */
export function restartTurnClock(state: PoisonMatchState, now: number): PoisonMatchState {
  const round = state.rounds[state.current];
  if (!round || !round.started || isRoundOver(round)) return state;
  return replaceRound(state, state.current, { ...round, turnEndsAt: now + state.turnMs });
}

export function turnRemainingMs(round: PoisonRound, now: number): number | null {
  return round.turnEndsAt === null ? null : Math.max(0, round.turnEndsAt - now);
}

/** Marker seen by accident: reshuffle and replay the round from the start. */
export function replayRound(state: PoisonMatchState): PoisonMatchState {
  const round = state.rounds[state.current];
  if (!round) return state;
  return replaceRound(state, state.current, {
    ...freshRound(round.starter),
    replays: round.replays + 1,
  });
}

export function undo(state: PoisonMatchState, now: number): PoisonMatchState {
  const round = state.rounds[state.current];
  if (!round) return state;
  if (round.picks.length > 0) {
    return replaceRound(state, state.current, {
      ...round,
      picks: round.picks.slice(0, -1),
      turnEndsAt: now + state.turnMs,
    });
  }
  if (round.started) {
    return replaceRound(state, state.current, { ...freshRound(round.starter), replays: round.replays });
  }
  return state.current > 0 ? { ...state, current: state.current - 1 } : state;
}

export function nextRound(state: PoisonMatchState): PoisonMatchState {
  const round = state.rounds[state.current];
  if (!round || !isRoundOver(round) || state.current >= state.rounds.length - 1) return state;
  return { ...state, current: state.current + 1 };
}

export function isFinished(state: PoisonMatchState): boolean {
  return state.rounds.every(isRoundOver);
}

export function roundsWon(state: PoisonMatchState): Record<Side, number> {
  const won: Record<Side, number> = { A: 0, B: 0 };
  for (const round of state.rounds) {
    const w = roundWinner(round);
    if (w) won[w]++;
  }
  return won;
}

/** Official values: 1 for each round a team won (both teams total 1 per round). */
export function teamValues(state: PoisonMatchState): Record<Side, number[]> {
  return {
    A: state.rounds.map((r) => (roundWinner(r) === "A" ? 1 : 0)),
    B: state.rounds.map((r) => (roundWinner(r) === "B" ? 1 : 0)),
  };
}

/** A random cup from those left (for the secret poison or the 5 s timeout). */
export function randomCup(cups: number[], random: () => number = Math.random): number | null {
  if (cups.length === 0) return null;
  return cups[Math.floor(random() * cups.length)];
}

function replaceRound(state: PoisonMatchState, index: number, round: PoisonRound): PoisonMatchState {
  return { ...state, rounds: state.rounds.map((r, i) => (i === index ? round : r)) };
}

// ---- Keeping the match across a refresh (this device only) ----

const storageKey = (fixtureId: string) => `murise:poison:${fixtureId}`;

export function loadPoisonMatch(fixtureId: string): PoisonMatchState | null {
  try {
    const raw = window.localStorage.getItem(storageKey(fixtureId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PoisonMatchState;
    return parsed?.version === 1 && parsed.fixtureId === fixtureId ? parsed : null;
  } catch {
    return null;
  }
}

export function storePoisonMatch(state: PoisonMatchState) {
  try {
    window.localStorage.setItem(storageKey(state.fixtureId), JSON.stringify(state));
  } catch {
    // The tracker still works; it just won't survive a refresh.
  }
}

export function clearPoisonMatch(fixtureId: string) {
  try {
    window.localStorage.removeItem(storageKey(fixtureId));
  } catch {
    // Nothing to clear.
  }
}

/** Poison style games: head-to-head, "Round 1..n", 1 point per round. */
export function isPoisonGame(game: {
  headToHead: boolean;
  maximumPerField: number;
  fields: string[];
}): boolean {
  return (
    game.headToHead &&
    game.maximumPerField === 1 &&
    game.fields.length > 0 &&
    game.fields.every((f) => /^Round \d+$/.test(f))
  );
}
