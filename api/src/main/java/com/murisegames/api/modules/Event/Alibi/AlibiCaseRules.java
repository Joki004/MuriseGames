package com.murisegames.api.modules.Event.Alibi;

import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.CaseContent;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.PreviewRole;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.Question;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.QuestionView;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.RolePreview;

import java.util.*;

/**
 * Pure rules for Alibi cases: cleaning what an organizer typed, deciding when a case
 * is ready to play, storing it as JSON and filtering it for each role.
 */
public final class AlibiCaseRules {

    public static final int QUESTION_COUNT = 10;
    public static final int MAX_FIXED_FACTS = 12;
    public static final int MAX_EQUIVALENTS = 15;

    private AlibiCaseRules() {
    }

    /** Thrown for content an organizer must fix; the message is shown to them. */
    public static final class InvalidCaseException extends RuntimeException {
        public InvalidCaseException(String message) {
            super(message);
        }
    }

    /**
     * Trims every text, drops blank lines and duplicate equivalents, and rejects content
     * the game cannot use. Incomplete content is allowed: a case can be saved as a draft.
     */
    public static CaseContent normalize(CaseContent raw) {
        if (raw == null) {
            return CaseContent.empty();
        }

        List<String> facts = cleanLines(raw.fixedFacts());
        if (facts.size() > MAX_FIXED_FACTS) {
            throw new InvalidCaseException(
                    "A case can have at most " + MAX_FIXED_FACTS + " fixed facts.");
        }

        List<Question> questions = new ArrayList<>();
        List<Question> rawQuestions = raw.questions() == null ? List.of() : raw.questions();
        for (int i = 0; i < rawQuestions.size(); i++) {
            Question q = rawQuestions.get(i);
            if (q == null) {
                continue;
            }
            String prompt = clean(q.prompt());
            List<String> equivalents = dedupeIgnoringCase(cleanLines(q.validEquivalents()));
            String note = clean(q.judgeNote());

            if (prompt.isEmpty()) {
                if (!equivalents.isEmpty() || !note.isEmpty()) {
                    throw new InvalidCaseException(
                            "Question " + (i + 1) + " has answers or notes but no question text.");
                }
                continue;
            }
            if (equivalents.size() > MAX_EQUIVALENTS) {
                throw new InvalidCaseException(
                        "Question " + (i + 1) + " can have at most "
                                + MAX_EQUIVALENTS + " valid equivalents.");
            }
            questions.add(new Question(prompt, equivalents, note));
        }
        if (questions.size() > QUESTION_COUNT) {
            throw new InvalidCaseException(
                    "A case has exactly " + QUESTION_COUNT + " questions; remove "
                            + (questions.size() - QUESTION_COUNT) + ".");
        }

        return new CaseContent(
                clean(raw.brief()),
                List.copyOf(facts),
                List.copyOf(questions),
                clean(raw.judgeRubric())
        );
    }

    /** What still blocks this case from being played; empty means ready. */
    public static List<String> missing(CaseContent content) {
        List<String> missing = new ArrayList<>();
        if (content.brief().isEmpty()) {
            missing.add("Write the brief the pair reads.");
        }
        if (content.fixedFacts().isEmpty()) {
            missing.add("Add at least one fixed fact.");
        }
        int count = content.questions().size();
        if (count < QUESTION_COUNT) {
            missing.add("Add " + (QUESTION_COUNT - count) + " more question"
                    + (QUESTION_COUNT - count == 1 ? "" : "s") + " (" + count + "/"
                    + QUESTION_COUNT + ").");
        }
        return missing;
    }

    public static boolean isReady(CaseContent content) {
        return missing(content).isEmpty();
    }

    /**
     * Filters a case for one role.
     * AUDIENCE (TV): the brief only.
     * PAIR (the two players): the brief and fixed facts while preparing, then the
     * question texts during interrogation; never equivalents or judge notes.
     * JUDGE: everything.
     */
    public static RolePreview view(
            CaseContent content,
            PreviewRole role,
            String label,
            int versionNumber
    ) {
        boolean judge = role == PreviewRole.JUDGE;
        boolean pair = role == PreviewRole.PAIR;

        List<QuestionView> questions = null;
        if (judge || pair) {
            questions = new ArrayList<>();
            for (int i = 0; i < content.questions().size(); i++) {
                Question q = content.questions().get(i);
                questions.add(new QuestionView(
                        i + 1,
                        q.prompt(),
                        judge ? q.validEquivalents() : null,
                        judge ? q.judgeNote() : null
                ));
            }
        }

        return new RolePreview(
                role,
                versionNumber,
                label,
                content.brief(),
                judge || pair ? content.fixedFacts() : null,
                questions,
                judge ? content.judgeRubric() : null
        );
    }

    // ---- JSON storage ------------------------------------------------------------

    public static Map<String, Object> toMap(CaseContent content) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Question q : content.questions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("prompt", q.prompt());
            row.put("validEquivalents", q.validEquivalents());
            row.put("judgeNote", q.judgeNote());
            questions.add(row);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("brief", content.brief());
        map.put("fixedFacts", content.fixedFacts());
        map.put("questions", questions);
        map.put("judgeRubric", content.judgeRubric());
        return map;
    }

    public static CaseContent fromMap(Map<String, Object> map) {
        if (map == null) {
            return CaseContent.empty();
        }
        List<Question> questions = new ArrayList<>();
        Object rawQuestions = map.get("questions");
        if (rawQuestions instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    questions.add(new Question(
                            text(row.get("prompt")),
                            strings(row.get("validEquivalents")),
                            text(row.get("judgeNote"))
                    ));
                }
            }
        }
        return new CaseContent(
                text(map.get("brief")),
                strings(map.get("fixedFacts")),
                List.copyOf(questions),
                text(map.get("judgeRubric"))
        );
    }

    // ---- helpers -----------------------------------------------------------------

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static List<String> cleanLines(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }
        return out;
    }

    private static List<String> dedupeIgnoringCase(List<String> values) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            unique.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
        }
        return List.copyOf(unique.values());
    }

    private static String text(Object value) {
        return value instanceof String s ? s : "";
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) {
                out.add(s);
            }
        }
        return List.copyOf(out);
    }
}
