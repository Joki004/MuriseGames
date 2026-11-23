package com.murisegames.api.modules.Event.Finale;

import com.murisegames.api.modules.Event.Title.TitleNormalizer;

import java.math.BigDecimal;
import java.util.*;

/**
 * GM-018 / GM-052 finale settlement rules for Secret Scene Premiere.
 * <p>
 * Pure and deterministic: no database, no clock, no randomness. Given every
 * guess made against one scene, it returns the exact ledger awards for that
 * scene's reveal. The same input always produces the same awards with the same
 * idempotency keys, so a repeated or rehearsed reveal cannot double-award
 * (the ledger rejects keys it has already recorded).
 * <p>
 * Rules (master slides 2, 5, 14):
 * <ul>
 *   <li>Stage 0 is before any clue; stages 1/2/3 follow each clue release.</li>
 *   <li>Only a team's EARLIEST correct guess counts: 4 / 3 / 2 / 1 points by stage.</li>
 *   <li>Wrong or blank guesses score 0. Self-guesses never score.</li>
 *   <li>Performer bonus: +1 for each opposing team that correctly identified the
 *       title after a clue (earliest correct stage >= 1). Agreed 23 Nov 2026: a team
 *       correct at stage 0 earns the performer nothing, even if it repeats the
 *       correct answer after a clue, because only the earliest correct guess counts.</li>
 *   <li>Leaked title (slide 14 notes): if an exact title leaks before the lock, every
 *       other team gets +2. Agreed 23 Nov 2026: the +2 is ADDED to any guessing points
 *       (e.g. 4 + 2 = 6) and does not change the performer bonus. See {@link #leakAwards}.</li>
 *   <li>Every guess must be resolved (no pending host review) before reveal.</li>
 * </ul>
 */
public final class FinaleScoring {

    public static final String RULE_VERSION = "secret-scene-finale-v1";

    private FinaleScoring() {
    }

    public enum Verdict { CORRECT, WRONG, BLANK, PENDING_REVIEW }

    public enum Category { GUESS, PERFORMER, LEAK }

    /** One stored guess, already matched (alias) or decided by the host. */
    public record GuessRecord(
            UUID guessId,
            UUID guessingTeamId,
            UUID targetSceneId,
            int stage,
            Verdict verdict
    ) {
    }

    /**
     * @param pointsByStage           index = stage, value = points for an earliest correct guess at that stage
     * @param performerBonusPerTeam   bonus the performing team earns per qualifying opposing team
     * @param firstPerformerBonusStage earliest stage that qualifies for the performer bonus
     * @param leakBonusPerTeam        points each other team receives when a title leaks before the lock
     */
    public record Rules(
            List<Integer> pointsByStage,
            int performerBonusPerTeam,
            int firstPerformerBonusStage,
            int leakBonusPerTeam
    ) {
        public static final Rules DEFAULT = new Rules(List.of(4, 3, 2, 1), 1, 1, 2);

        public Rules {
            pointsByStage = List.copyOf(pointsByStage);
            if (pointsByStage.isEmpty()) throw new IllegalArgumentException("pointsByStage must not be empty");
        }

        int maxStage() {
            return pointsByStage.size() - 1;
        }
    }

    /** One ledger line to write. {@code teamId} receives the points. */
    public record Award(
            Category category,
            UUID teamId,
            UUID sceneId,
            UUID sourceTeamId,
            UUID sourceGuessId,
            int stage,
            BigDecimal points,
            String idempotencyKey
    ) {
    }

    /** What the reveal shows: who got it right and at which stage. */
    public record CorrectGuesser(UUID teamId, int earliestStage, int points) {
    }

    public record SceneSettlement(
            UUID sceneId,
            UUID performerTeamId,
            List<CorrectGuesser> correctGuessers,
            List<Award> awards
    ) {
        public BigDecimal totalFor(UUID teamId, Category category) {
            return awards.stream()
                    .filter(a -> a.teamId().equals(teamId) && a.category() == category)
                    .map(Award::points)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /**
     * GM-016 verdict for one guess.
     * A host decision ("correct"/"wrong") always wins. Otherwise a guess whose
     * normalized text equals the title or an approved alias is correct; anything
     * else waits for host review. Blank guesses are wrong.
     */
    public static Verdict verdictFor(
            String rawGuess,
            String normalizedTitle,
            Collection<String> normalizedAliases,
            String hostDecision
    ) {
        if ("correct".equals(hostDecision)) return Verdict.CORRECT;
        if ("wrong".equals(hostDecision)) return Verdict.WRONG;

        String normalized = TitleNormalizer.normalize(rawGuess);
        if (normalized.isEmpty()) return Verdict.BLANK;
        if (normalized.equals(normalizedTitle) || normalizedAliases.contains(normalized)) {
            return Verdict.CORRECT;
        }
        return Verdict.PENDING_REVIEW;
    }

    public static SceneSettlement settleScene(
            UUID eventId,
            UUID sceneId,
            UUID performerTeamId,
            Collection<GuessRecord> guesses,
            Rules rules
    ) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(sceneId, "sceneId");
        Objects.requireNonNull(performerTeamId, "performerTeamId");
        Objects.requireNonNull(rules, "rules");

        List<GuessRecord> forScene = guesses.stream()
                .filter(g -> sceneId.equals(g.targetSceneId()))
                .toList();

        List<UUID> pending = forScene.stream()
                .filter(g -> g.verdict() == Verdict.PENDING_REVIEW)
                .map(GuessRecord::guessId)
                .toList();
        if (!pending.isEmpty()) {
            throw new IllegalStateException(
                    "Scene " + sceneId + " has " + pending.size() + " guess(es) awaiting host review; resolve them before reveal.");
        }

        // Earliest correct guess per opposing team (ties on stage -> lowest guess id, for determinism).
        Map<UUID, GuessRecord> earliestCorrect = new TreeMap<>();
        for (GuessRecord g : forScene) {
            if (g.verdict() != Verdict.CORRECT) continue;
            if (g.guessingTeamId().equals(performerTeamId)) continue; // self-guess never scores
            if (g.stage() < 0 || g.stage() > rules.maxStage()) continue;
            earliestCorrect.merge(g.guessingTeamId(), g, FinaleScoring::earlier);
        }

        List<CorrectGuesser> correct = new ArrayList<>();
        List<Award> awards = new ArrayList<>();
        String base = "finale:" + eventId + ":" + sceneId + ":";

        for (GuessRecord g : earliestCorrect.values()) {
            int points = rules.pointsByStage().get(g.stage());
            correct.add(new CorrectGuesser(g.guessingTeamId(), g.stage(), points));
            if (points > 0) {
                awards.add(new Award(
                        Category.GUESS, g.guessingTeamId(), sceneId, g.guessingTeamId(), g.guessId(),
                        g.stage(), BigDecimal.valueOf(points),
                        base + "GUESS:" + g.guessingTeamId()
                ));
            }
            if (g.stage() >= rules.firstPerformerBonusStage() && rules.performerBonusPerTeam() > 0) {
                awards.add(new Award(
                        Category.PERFORMER, performerTeamId, sceneId, g.guessingTeamId(), g.guessId(),
                        g.stage(), BigDecimal.valueOf(rules.performerBonusPerTeam()),
                        base + "PERFORMER:" + g.guessingTeamId()
                ));
            }
        }

        correct.sort(Comparator.comparingInt(CorrectGuesser::earliestStage)
                .thenComparing(CorrectGuesser::teamId));
        return new SceneSettlement(sceneId, performerTeamId, List.copyOf(correct), List.copyOf(awards));
    }

    /**
     * Leaked title: every active team except the performing team gets the leak bonus once.
     * Keys are stable per (scene, receiving team), so recording the same leak twice awards nothing new.
     */
    public static List<Award> leakAwards(
            UUID eventId,
            UUID sceneId,
            UUID performerTeamId,
            Collection<UUID> activeTeamIds,
            Rules rules
    ) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(sceneId, "sceneId");
        Objects.requireNonNull(performerTeamId, "performerTeamId");
        if (rules.leakBonusPerTeam() <= 0) return List.of();

        String base = leakKeyPrefix(eventId, sceneId);
        return new TreeSet<>(activeTeamIds).stream()
                .filter(teamId -> !teamId.equals(performerTeamId))
                .map(teamId -> new Award(
                        Category.LEAK, teamId, sceneId, performerTeamId, null, -1,
                        BigDecimal.valueOf(rules.leakBonusPerTeam()),
                        base + teamId
                ))
                .toList();
    }

    /** Ledger source_key prefix shared by every leak award for one scene. */
    public static String leakKeyPrefix(UUID eventId, UUID sceneId) {
        return "finale:" + eventId + ":" + sceneId + ":LEAK:";
    }

    private static GuessRecord earlier(GuessRecord a, GuessRecord b) {
        if (a.stage() != b.stage()) return a.stage() < b.stage() ? a : b;
        return a.guessId().compareTo(b.guessId()) <= 0 ? a : b;
    }
}
