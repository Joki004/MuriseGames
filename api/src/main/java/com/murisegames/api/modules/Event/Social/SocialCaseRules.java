package com.murisegames.api.modules.Event.Social;

import com.murisegames.api.modules.Event.Social.SocialCaseTypes.*;

import java.util.*;

public final class SocialCaseRules {

    public static final int MAX_ITEMS = 20;

    private SocialCaseRules() {
    }

    public static final class InvalidCaseException extends RuntimeException {
        public InvalidCaseException(String message) {
            super(message);
        }
    }

    public static CaseContent normalize(CaseContent raw) {
        if (raw == null) {
            return CaseContent.empty();
        }
        List<String> defGoals = cleanLines(raw.defenderGoals());
        List<String> defConstraints = cleanLines(raw.defenderConstraints());
        List<String> attRedFlags = cleanLines(raw.attackerRedFlags());
        List<String> attBait = cleanLines(raw.attackerBait());

        validateCap("Defender goals", defGoals.size());
        validateCap("Defender constraints", defConstraints.size());
        validateCap("Attacker red flags", attRedFlags.size());
        validateCap("Attacker bait", attBait.size());

        JudgeRubric rawRubric = raw.judgeRubric() == null ? JudgeRubric.empty() : raw.judgeRubric();
        List<String> criteria = cleanLines(rawRubric.criteria());
        validateCap("Judge rubric criteria", criteria.size());

        return new CaseContent(
                clean(raw.publicSetup()),
                List.copyOf(defGoals),
                List.copyOf(defConstraints),
                List.copyOf(attRedFlags),
                List.copyOf(attBait),
                new JudgeRubric(clean(rawRubric.summary()), List.copyOf(criteria))
        );
    }

    public static List<String> missing(CaseContent content) {
        List<String> missing = new ArrayList<>();
        if (content.publicSetup().isEmpty()) {
            missing.add("Write the public setup for the scenario.");
        }
        if (content.defenderGoals().isEmpty()) {
            missing.add("Add at least one defender goal.");
        }
        if (content.defenderConstraints().isEmpty()) {
            missing.add("Add at least one defender constraint.");
        }
        if (content.attackerRedFlags().isEmpty()) {
            missing.add("Add at least one attacker red flag.");
        }
        if (content.attackerBait().isEmpty()) {
            missing.add("Add at least one attacker bait item.");
        }
        JudgeRubric rubric = content.judgeRubric() == null ? JudgeRubric.empty() : content.judgeRubric();
        if (rubric.summary().isEmpty() && rubric.criteria().isEmpty()) {
            missing.add("Add a judge rubric summary or scoring criteria.");
        }
        return missing;
    }

    public static boolean isReady(CaseContent content) {
        return missing(content).isEmpty();
    }

    /**
     * Projects only the fields permitted for the active role:
     * - DEFENDER: publicSetup + defenderGoals + defenderConstraints
     * - ATTACKER: publicSetup + attackerRedFlags + attackerBait
     * - JUDGE: all fields including judgeRubric
     * - AUDIENCE: publicSetup only
     */
    public static RoleCardView cardForRole(
            RoleCardType role,
            UUID caseId,
            int versionNumber,
            String label,
            CaseContent content,
            Map<String, Object> rawRubricMap
    ) {
        boolean isDefender = role == RoleCardType.DEFENDER;
        boolean isAttacker = role == RoleCardType.ATTACKER;
        boolean isJudge = role == RoleCardType.JUDGE;

        return new RoleCardView(
                role,
                caseId,
                versionNumber,
                label,
                content.publicSetup(),
                isDefender || isJudge ? content.defenderGoals() : null,
                isDefender || isJudge ? content.defenderConstraints() : null,
                isAttacker || isJudge ? content.attackerRedFlags() : null,
                isAttacker || isJudge ? content.attackerBait() : null,
                isJudge ? content.judgeRubric() : null,
                isJudge ? rawRubricMap : null
        );
    }

    public static Map<String, Object> rubricToMap(JudgeRubric rubric) {
        JudgeRubric safe = rubric == null ? JudgeRubric.empty() : rubric;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("summary", clean(safe.summary()));
        map.put("criteria", cleanLines(safe.criteria()));
        return map;
    }

    public static JudgeRubric rubricFromMap(Map<String, Object> map) {
        if (map == null) {
            return JudgeRubric.empty();
        }
        String summary = map.get("summary") instanceof String s ? s : "";
        List<String> criteria = new ArrayList<>();
        if (map.get("criteria") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s && !s.isBlank()) {
                    criteria.add(s.strip());
                }
            }
        }
        return new JudgeRubric(summary.strip(), List.copyOf(criteria));
    }

    private static void validateCap(String label, int count) {
        if (count > MAX_ITEMS) {
            throw new InvalidCaseException(label + " can have at most " + MAX_ITEMS + " items.");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static List<String> cleanLines(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String v : values) {
            String cleaned = clean(v);
            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }
        return out;
    }
}