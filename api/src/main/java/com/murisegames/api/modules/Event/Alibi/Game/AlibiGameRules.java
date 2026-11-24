package com.murisegames.api.modules.Event.Alibi.Game;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Pure rules for playing Unified Alibi (GM-046): timings, automatic verdicts for the
 * obvious cases, round scores and which cases a team may be given.
 */
public final class AlibiGameRules {

    public static final Duration PREPARATION = Duration.ofSeconds(90);
    public static final Duration QUESTION_TIME = Duration.ofSeconds(30);
    /** Network slack after a question closes before a missing answer is recorded. */
    public static final Duration GRACE = Duration.ofSeconds(3);
    public static final int MAX_ANSWER_LENGTH = 300;

    private AlibiGameRules() {
    }

    /** Lower case, no accents, no punctuation, single spaces: "  Pizza, Margherita!" -> "pizza margherita". */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String stripped = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return stripped.replaceAll("\\s+", " ");
    }

    public static boolean isBlank(String text) {
        return normalize(text).isEmpty();
    }

    /**
     * Decides only what needs no judge; null means a judge must decide.
     * - A blank or missing answer on either side scores 0 (two blanks never match).
     * - Fixed-fact question (has equivalents): both answers equal an accepted answer -> match.
     * - Otherwise identical answers (after normalizing) -> match.
     */
    public static Boolean autoVerdict(String answerA, String answerB, List<String> equivalents) {
        if (isBlank(answerA) || isBlank(answerB)) {
            return false;
        }
        String a = normalize(answerA);
        String b = normalize(answerB);
        if (equivalents != null && !equivalents.isEmpty()) {
            Set<String> accepted = new HashSet<>();
            for (String e : equivalents) {
                accepted.add(normalize(e));
            }
            if (accepted.contains(a) && accepted.contains(b)) {
                return true;
            }
            return null;
        }
        return a.equals(b) ? Boolean.TRUE : null;
    }

    /** A question is over when both sides answered, or its time plus grace has passed. */
    public static boolean questionOver(int lockedSides, Instant closesAt, Instant now) {
        return lockedSides >= 2 || !now.isBefore(closesAt.plus(GRACE));
    }

    public static int score(Collection<Boolean> verdicts) {
        int matches = 0;
        for (Boolean verdict : verdicts) {
            if (Boolean.TRUE.equals(verdict)) {
                matches++;
            }
        }
        return matches;
    }

    /** One candidate in the case pool, as seen by the picker. */
    public record CaseCandidate(UUID caseId, String label, boolean ready, Set<UUID> knownBy) {
    }

    /**
     * Cases a team may be given: ready, never used in this event, and unknown to every
     * member of the team (nobody in it wrote, edited or opened it).
     */
    public static List<CaseCandidate> eligible(
            List<CaseCandidate> pool,
            Set<UUID> usedCaseIds,
            Set<UUID> teamMemberIds
    ) {
        List<CaseCandidate> out = new ArrayList<>();
        for (CaseCandidate candidate : pool) {
            if (!candidate.ready() || usedCaseIds.contains(candidate.caseId())) {
                continue;
            }
            boolean knownByTeam = candidate.knownBy().stream().anyMatch(teamMemberIds::contains);
            if (!knownByTeam) {
                out.add(candidate);
            }
        }
        return out;
    }
}
