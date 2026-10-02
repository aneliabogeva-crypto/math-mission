package bg.mathmission.content;

import java.util.List;
import java.util.Map;

/**
 * JSON shapes stored inside a question. The prompt is safe to send to students; the answer key,
 * solution and distractor explanations are server-only until the configured review moment.
 */
public final class QuestionModel {

    private QuestionModel() {}

    public enum ResponseType {
        SINGLE_CHOICE, NUMERIC, EXPRESSION, STEPS, STRUCTURED, FREE_TEXT
    }

    /** Required form of a symbolic answer. */
    public enum AnswerForm {
        ANY, NORMAL_FORM, FACTORISED
    }

    public enum Misconception {
        SIGN("Знаци"),
        BRACKETS("Скоби"),
        ORDER_OF_OPERATIONS("Ред на действията"),
        LIKE_TERMS("Подобни едночлени"),
        FORMULA_APPLICATION("Формули за съкратено умножение"),
        FACTORISATION("Разлагане на множители"),
        REASONING("Разсъждение"),
        TECHNICAL("Техническа грешка");

        public final String labelBg;

        Misconception(String labelBg) {
            this.labelBg = labelBg;
        }
    }

    public record Option(String id, String text) {}

    public record PartPrompt(String id, String label, ResponseType type, double points) {}

    /** Student-visible part of a question. */
    public record Prompt(String text, List<Option> options, List<PartPrompt> parts,
                         String startExpression, String inputHint) {}

    public record PartKey(String id, ResponseType type, String answer, AnswerForm form, double points) {}

    /** A likely wrong answer, with the misconception it signals and a respectful explanation. */
    public record Distractor(String match, Misconception misconception, String explanation) {}

    /** Server-only answer key. */
    public record AnswerKey(
            String correctOptionId,
            String answer,
            AnswerForm form,
            List<PartKey> parts,
            List<Distractor> distractors,
            Misconception defaultMisconception,
            String confirmation,
            String solution,
            String rubric) {

        public AnswerForm formOrAny() {
            return form == null ? AnswerForm.ANY : form;
        }
    }

    /** What the client sends for one item. */
    public record AnswerPayload(String optionId, String value, List<String> steps,
                                Map<String, String> parts, String text) {}
}
