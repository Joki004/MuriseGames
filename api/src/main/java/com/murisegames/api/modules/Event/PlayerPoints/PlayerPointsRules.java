package com.murisegames.api.modules.Event.PlayerPoints;

import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.Credit;
import com.murisegames.api.modules.Event.PlayerPoints.PlayerPointsTypes.LiveAward;
import com.murisegames.api.modules.Event.Scoring.Adapter.GameScoreAdapters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Pure rules for star-player credits, free of Spring for easy testing. */
public final class PlayerPointsRules {

    /** At most this many players can be credited for one segment. */
    static final int MAX_CREDITS_PER_SEGMENT = 8;

    private PlayerPointsRules() {
    }

    /**
     * Segments a referee can credit players for. Scored games use their scoring
     * fields (Duel 1..4, Round 1..2...). Games without a reviewed formula yet are
     * credited per match. Unscored or finale activities have none.
     */
    public static List<String> segmentsFor(String scoringHandler) {
        if (scoringHandler == null
                || scoringHandler.startsWith("unscored")
                || scoringHandler.equals("secret_scene_premiere_v1")) {
            return List.of();
        }

        GameScoreAdapters.Definition definition = GameScoreAdapters.find(scoringHandler);
        return definition == null ? List.of("Match") : List.copyOf(definition.fields());
    }

    /** Validated, de-duplicated credits; throws IllegalArgumentException with a readable message. */
    public static List<Credit> validate(
            Collection<Credit> requested,
            int segmentCount,
            Set<UUID> eligibleParticipants
    ) {
        if (requested == null) {
            throw new IllegalArgumentException("Credits are required (an empty list clears them).");
        }

        Set<Credit> unique = new LinkedHashSet<>();
        int[] perSegment = new int[Math.max(segmentCount, 0)];

        for (Credit credit : requested) {
            if (credit == null || credit.participantId() == null) {
                throw new IllegalArgumentException("Each credit needs a player.");
            }

            if (credit.segmentIndex() < 0 || credit.segmentIndex() >= segmentCount) {
                throw new IllegalArgumentException("That part of the match does not exist.");
            }

            if (!eligibleParticipants.contains(credit.participantId())) {
                throw new IllegalArgumentException(
                        "Only players of the two teams in this match can be credited.");
            }

            if (unique.add(credit) && ++perSegment[credit.segmentIndex()] > MAX_CREDITS_PER_SEGMENT) {
                throw new IllegalArgumentException(
                        "At most " + MAX_CREDITS_PER_SEGMENT + " players per part of the match.");
            }
        }

        return List.copyOf(unique);
    }

    public record Diff(List<Credit> toAward, List<LiveAward> toReverse) {

        public boolean isEmpty() {
            return toAward.isEmpty() && toReverse.isEmpty();
        }
    }

    /** What to append so the live credits become exactly the requested ones. */
    public static Diff diff(Collection<LiveAward> live, Collection<Credit> requested) {
        Set<Credit> wanted = new LinkedHashSet<>(requested);
        Set<Credit> kept = new LinkedHashSet<>();
        List<LiveAward> toReverse = new ArrayList<>();

        for (LiveAward award : live) {
            Credit credit = new Credit(award.segmentIndex(), award.participantId());

            // A duplicate live award (should not happen) is reversed as well.
            if (wanted.contains(credit) && kept.add(credit)) {
                continue;
            }

            toReverse.add(award);
        }

        List<Credit> toAward = wanted.stream().filter(c -> !kept.contains(c)).toList();
        return new Diff(toAward, List.copyOf(toReverse));
    }

    /** Standard competition ranking (1, 2, 2, 4) on rows already sorted by points, highest first. */
    public static <T> int[] ranks(List<T> sortedByPointsDesc, ToIntFunction<T> points) {
        int[] ranks = new int[sortedByPointsDesc.size()];

        for (int i = 0; i < ranks.length; i++) {
            boolean tie = i > 0
                    && points.applyAsInt(sortedByPointsDesc.get(i))
                    == points.applyAsInt(sortedByPointsDesc.get(i - 1));
            ranks[i] = tie ? ranks[i - 1] : i + 1;
        }

        return ranks;
    }
}
