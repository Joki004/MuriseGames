/**
 * Points for an earliest correct Secret Scene guess by clue stage (rulebook slides 5 & 14):
 * 4 before any clue, then 3, 2, 1 after clues 1, 2, 3. Mirrors FinaleScoring.Rules.DEFAULT.
 */
const POINTS_BY_STAGE = [4, 3, 2, 1];

export function guessPointsForStage(stage: number): number {
  return POINTS_BY_STAGE[Math.min(Math.max(stage, 0), POINTS_BY_STAGE.length - 1)];
}

export function stageTierLabel(stage: number): string {
  return stage === 0 ? "before any clue" : `after clue ${stage}`;
}
