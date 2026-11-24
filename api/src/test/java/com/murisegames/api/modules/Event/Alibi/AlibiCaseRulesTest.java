package com.murisegames.api.modules.Event.Alibi;

import com.murisegames.api.modules.Event.Alibi.AlibiCaseRules.InvalidCaseException;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.CaseContent;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.PreviewRole;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.Question;
import com.murisegames.api.modules.Event.Alibi.AlibiCaseTypes.RolePreview;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlibiCaseRulesTest {

    private static Question q(String prompt, String note, String... equivalents) {
        return new Question(prompt, Arrays.asList(equivalents), note);
    }

    private static List<Question> tenQuestions() {
        List<Question> list = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            list.add(q("Question " + i, "", "answer " + i));
        }
        return list;
    }

    private static CaseContent readyCase() {
        return new CaseContent(
                "You had dinner together last night.",
                List.of("The restaurant was Italian.", "You left at 22:00."),
                tenQuestions(),
                "Accept any time between 21:45 and 22:15."
        );
    }

    @Test
    void normalizeTrimsAndDropsBlankLinesAndDuplicateEquivalents() {
        CaseContent raw = new CaseContent(
                "  Brief  ",
                Arrays.asList(" Fact A ", "", null, "Fact B"),
                Arrays.asList(
                        q("  What time did you leave? ", "  note ", "22:00", " 10pm ", "10PM", ""),
                        q("   ", "", ""),
                        null
                ),
                null
        );

        CaseContent clean = AlibiCaseRules.normalize(raw);

        assertThat(clean.brief()).isEqualTo("Brief");
        assertThat(clean.fixedFacts()).containsExactly("Fact A", "Fact B");
        assertThat(clean.questions()).hasSize(1);
        assertThat(clean.questions().get(0).prompt()).isEqualTo("What time did you leave?");
        assertThat(clean.questions().get(0).validEquivalents()).containsExactly("22:00", "10pm");
        assertThat(clean.questions().get(0).judgeNote()).isEqualTo("note");
        assertThat(clean.judgeRubric()).isEmpty();
    }

    @Test
    void aQuestionWithAnswersButNoTextIsRejected() {
        CaseContent raw = new CaseContent("", List.of(), List.of(q(" ", "", "22:00")), "");

        assertThatThrownBy(() -> AlibiCaseRules.normalize(raw))
                .isInstanceOf(InvalidCaseException.class)
                .hasMessageContaining("Question 1");
    }

    @Test
    void moreThanTenQuestionsIsRejected() {
        List<Question> eleven = new ArrayList<>(tenQuestions());
        eleven.add(q("One too many", ""));

        assertThatThrownBy(() -> AlibiCaseRules.normalize(
                new CaseContent("Brief", List.of("Fact"), eleven, "")))
                .isInstanceOf(InvalidCaseException.class)
                .hasMessageContaining("remove 1");
    }

    @Test
    void aDraftIsAllowedButReportsWhatIsMissing() {
        CaseContent draft = AlibiCaseRules.normalize(
                new CaseContent("", List.of(), List.of(q("Only one", "")), ""));

        assertThat(AlibiCaseRules.isReady(draft)).isFalse();
        assertThat(AlibiCaseRules.missing(draft)).containsExactly(
                "Write the brief the pair reads.",
                "Add at least one fixed fact.",
                "Add 9 more questions (1/10)."
        );
    }

    @Test
    void aCompleteCaseIsReady() {
        assertThat(AlibiCaseRules.missing(AlibiCaseRules.normalize(readyCase()))).isEmpty();
    }

    @Test
    void audienceSeesOnlyTheBrief() {
        RolePreview view = AlibiCaseRules.view(readyCase(), PreviewRole.AUDIENCE, "Case 1", 3);

        assertThat(view.brief()).isEqualTo("You had dinner together last night.");
        assertThat(view.fixedFacts()).isNull();
        assertThat(view.questions()).isNull();
        assertThat(view.judgeRubric()).isNull();
        assertThat(view.versionNumber()).isEqualTo(3);
    }

    @Test
    void pairSeesFactsAndQuestionTextButNeverAnswersOrNotes() {
        RolePreview view = AlibiCaseRules.view(readyCase(), PreviewRole.PAIR, "Case 1", 1);

        assertThat(view.fixedFacts()).hasSize(2);
        assertThat(view.questions()).hasSize(10);
        assertThat(view.questions().get(0).number()).isEqualTo(1);
        assertThat(view.questions()).allSatisfy(question -> {
            assertThat(question.validEquivalents()).isNull();
            assertThat(question.judgeNote()).isNull();
        });
        assertThat(view.judgeRubric()).isNull();
    }

    @Test
    void judgeSeesEverything() {
        RolePreview view = AlibiCaseRules.view(readyCase(), PreviewRole.JUDGE, "Case 1", 1);

        assertThat(view.questions().get(4).validEquivalents()).containsExactly("answer 5");
        assertThat(view.judgeRubric()).startsWith("Accept any time");
    }

    @Test
    void jsonStorageRoundTripsExactly() {
        CaseContent content = AlibiCaseRules.normalize(readyCase());

        assertThat(AlibiCaseRules.fromMap(AlibiCaseRules.toMap(content))).isEqualTo(content);
    }

    @Test
    void savingTheSameContentAgainIsDetectedAsUnchanged() {
        CaseContent first = AlibiCaseRules.normalize(readyCase());
        CaseContent stored = AlibiCaseRules.fromMap(AlibiCaseRules.toMap(first));
        CaseContent again = AlibiCaseRules.normalize(readyCase());

        assertThat(again).isEqualTo(stored);
    }
}
