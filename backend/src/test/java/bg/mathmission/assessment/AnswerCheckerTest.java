package bg.mathmission.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import bg.mathmission.content.QuestionModel.AnswerForm;
import bg.mathmission.content.QuestionModel.AnswerKey;
import bg.mathmission.content.QuestionModel.AnswerPayload;
import bg.mathmission.content.QuestionModel.Distractor;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.QuestionModel.PartKey;
import bg.mathmission.content.QuestionModel.ResponseType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnswerCheckerTest {

    private static AnswerPayload value(String v) {
        return new AnswerPayload(null, v, null, null, null);
    }

    @Test
    void choiceUsesDistractorExplanation() {
        AnswerKey k = new AnswerKey("b", null, null, null,
                List.of(new Distractor("a", Misconception.SIGN, "Провери знака.")), Misconception.REASONING, null, "sol", null);
        var wrong = AnswerChecker.check(ResponseType.SINGLE_CHOICE, k, 1, new AnswerPayload("a", null, null, null, null));
        assertThat(wrong.status()).isEqualTo(AnswerChecker.Status.INCORRECT);
        assertThat(wrong.misconception()).isEqualTo(Misconception.SIGN);
        assertThat(wrong.message()).isEqualTo("Провери знака.");
        var right = AnswerChecker.check(ResponseType.SINGLE_CHOICE, k, 1, new AnswerPayload("b", null, null, null, null));
        assertThat(right.isCorrect()).isTrue();
        assertThat(right.message()).isNotBlank(); // confirmation even after a correct answer
    }

    @Test
    void numericAcceptsCommaAndPointAndDetectsSignErrors() {
        AnswerKey k = new AnswerKey(null, "-6", null, null, List.of(), Misconception.SIGN, null, "s", null);
        assertThat(AnswerChecker.check(ResponseType.NUMERIC, k, 2, value("-6,0")).isCorrect()).isTrue();
        assertThat(AnswerChecker.check(ResponseType.NUMERIC, k, 2, value("-6.0")).isCorrect()).isTrue();
        var sign = AnswerChecker.check(ResponseType.NUMERIC, k, 2, value("6"));
        assertThat(sign.misconception()).isEqualTo(Misconception.SIGN);
        assertThat(AnswerChecker.check(ResponseType.NUMERIC, k, 2, value("x")).status()).isEqualTo(AnswerChecker.Status.INVALID_INPUT);
    }

    @Test
    void expressionRequiresFormForFullCredit() {
        AnswerKey k = new AnswerKey(null, "x^2 + 4x", AnswerForm.NORMAL_FORM, null, List.of(), Misconception.LIKE_TERMS, null, "s", null);
        assertThat(AnswerChecker.check(ResponseType.EXPRESSION, k, 2, value("4x + x²")).isCorrect()).isTrue();
        var partial = AnswerChecker.check(ResponseType.EXPRESSION, k, 2, value("x(x + 4)"));
        assertThat(partial.status()).isEqualTo(AnswerChecker.Status.PARTIAL);
        assertThat(partial.points()).isEqualTo(1.0);
    }

    @Test
    void factorisedFormIsChecked() {
        AnswerKey k = new AnswerKey(null, "6x^2 - 9x", AnswerForm.FACTORISED, null, List.of(), Misconception.FACTORISATION, null, "s", null);
        assertThat(AnswerChecker.check(ResponseType.EXPRESSION, k, 2, value("3x(2x - 3)")).isCorrect()).isTrue();
        assertThat(AnswerChecker.check(ResponseType.EXPRESSION, k, 2, value("x(6x - 9)")).status()).isEqualTo(AnswerChecker.Status.PARTIAL);
    }

    @Test
    void stepsGivePartialCreditAndLocateTheError() {
        AnswerKey k = new AnswerKey(null, "7x^2y + 2xy^2", AnswerForm.NORMAL_FORM,
                List.of(new PartKey("start", ResponseType.EXPRESSION, "3x^2y - 2xy^2 + 5x^2y + 4xy^2 - x^2y", null, 0)),
                List.of(), Misconception.LIKE_TERMS, null, "s", null);
        var ok = AnswerChecker.check(ResponseType.STEPS, k, 3,
                new AnswerPayload(null, null, List.of("8x^2y + 2xy^2 - x^2y", "7x^2y + 2xy^2"), null, null));
        assertThat(ok.isCorrect()).isTrue();
        var bad = AnswerChecker.check(ResponseType.STEPS, k, 3,
                new AnswerPayload(null, null, List.of("8x^2y + 2xy^2 - x^2y", "9x^2y + 2xy^2"), null, null));
        assertThat(bad.errorStep()).isEqualTo(2);
        assertThat(bad.points()).isGreaterThan(0).isLessThanOrEqualTo(1.5);
    }

    @Test
    void structuredGivesPartialCredit() {
        AnswerKey k = new AnswerKey(null, null, null, List.of(
                new PartKey("a", ResponseType.EXPRESSION, "-6x^3y^4", AnswerForm.NORMAL_FORM, 1),
                new PartKey("b", ResponseType.NUMERIC, "7", null, 1)), List.of(), Misconception.TECHNICAL, null, "s", "r");
        var r = AnswerChecker.check(ResponseType.STRUCTURED, k, 2, new AnswerPayload(null, null, null, Map.of("a", "-6x³y⁴", "b", "6"), null));
        assertThat(r.status()).isEqualTo(AnswerChecker.Status.PARTIAL);
        assertThat(r.points()).isEqualTo(1.0);
        assertThat(r.parts()).hasSize(2);
    }

    @Test
    void freeTextGoesToTeacherReview() {
        AnswerKey k = new AnswerKey(null, null, null, null, List.of(), Misconception.REASONING, null, "s", "rubric");
        assertThat(AnswerChecker.check(ResponseType.FREE_TEXT, k, 3, new AnswerPayload(null, null, null, null, "Защото..."))
                .status()).isEqualTo(AnswerChecker.Status.NEEDS_REVIEW);
    }

    @Test
    void feedbackNeverUsesShamingLanguage() {
        AnswerKey k = new AnswerKey(null, "5", null, null, List.of(), Misconception.REASONING, null, "s", null);
        String msg = AnswerChecker.check(ResponseType.NUMERIC, k, 1, value("4")).message().toLowerCase();
        assertThat(msg).doesNotContain("грешно!", "провал", "слаб");
        assertThat(msg).contains("верният отговор е 5").contains("решението");
    }

    @Test
    void wrongExpressionGetsAConcreteNumericExplanation() {
        AnswerKey k = new AnswerKey(null, "x^2 + 4x", AnswerForm.NORMAL_FORM, null, List.of(), Misconception.LIKE_TERMS, null, "s", null);
        String msg = AnswerChecker.check(ResponseType.EXPRESSION, k, 1, value("x^2 + 3x")).message();
        assertThat(msg).contains("при x = 2").contains("10").contains("12");
    }

    @Test
    void mismatchedPayloadShapeNeverThrows() {
        AnswerKey steps = new AnswerKey(null, "x", AnswerForm.NORMAL_FORM, List.of(), List.of(), Misconception.LIKE_TERMS, null, "s", null);
        AnswerPayload choiceOnly = new AnswerPayload("b", null, null, null, null);
        for (ResponseType t : List.of(ResponseType.STEPS, ResponseType.EXPRESSION, ResponseType.NUMERIC)) {
            assertThat(AnswerChecker.check(t, steps, 1, choiceOnly).points()).isZero();
        }
    }
}
