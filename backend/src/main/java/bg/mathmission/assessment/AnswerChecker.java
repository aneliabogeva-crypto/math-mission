package bg.mathmission.assessment;

import bg.mathmission.content.QuestionModel.AnswerForm;
import bg.mathmission.content.QuestionModel.AnswerKey;
import bg.mathmission.content.QuestionModel.AnswerPayload;
import bg.mathmission.content.QuestionModel.Distractor;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.QuestionModel.PartKey;
import bg.mathmission.content.QuestionModel.ResponseType;
import bg.mathmission.math.MathEngine;
import bg.mathmission.math.MathInputException;
import bg.mathmission.math.Rational;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, side-effect-free scoring of a single response. Student input is parsed through
 * the restricted grammar in {@link MathEngine}; nothing is executed.
 */
public final class AnswerChecker {

    private AnswerChecker() {}

    public enum Status { CORRECT, PARTIAL, INCORRECT, INVALID_INPUT, NEEDS_REVIEW, UNANSWERED }

    public record PartResult(String id, Status status, double points, String message) {}

    public record Result(
            Status status,
            double points,
            double maxPoints,
            Misconception misconception,
            String message,
            Integer errorStep,
            List<PartResult> parts) {

        public boolean isCorrect() {
            return status == Status.CORRECT;
        }
    }

    public static Result check(ResponseType type, AnswerKey key, double maxPoints, AnswerPayload p) {
        if (p == null || isBlank(p)) {
            return new Result(Status.UNANSWERED, 0, maxPoints, null, "Няма отговор.", null, List.of());
        }
        try {
            return switch (type) {
                case SINGLE_CHOICE -> checkChoice(key, maxPoints, p.optionId());
                case NUMERIC -> checkNumeric(key, maxPoints, p.value());
                case EXPRESSION -> checkExpression(key, key.answer(), key.formOrAny(), maxPoints, p.value());
                case STEPS -> checkSteps(key, maxPoints, p.steps());
                case STRUCTURED -> checkStructured(key, maxPoints, p.parts());
                case FREE_TEXT -> new Result(Status.NEEDS_REVIEW, 0, maxPoints, null,
                        "Отговорът ще бъде прегледан от учител.", null, List.of());
            };
        } catch (MathInputException e) {
            return new Result(Status.INVALID_INPUT, 0, maxPoints, Misconception.TECHNICAL,
                    "Не успяхме да прочетем записа: " + e.getMessage(), null, List.of());
        }
    }

    private static boolean isBlank(AnswerPayload p) {
        return blank(p.optionId()) && blank(p.value()) && blank(p.text())
                && (p.steps() == null || p.steps().stream().allMatch(AnswerChecker::blank))
                && (p.parts() == null || p.parts().values().stream().allMatch(AnswerChecker::blank));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String confirmation(AnswerKey key) {
        return key.confirmation() != null ? key.confirmation() : "Вярно! Подходът ти е правилен.";
    }

    private static Result checkChoice(AnswerKey key, double max, String optionId) {
        if (key.correctOptionId().equals(optionId)) {
            return new Result(Status.CORRECT, max, max, null, confirmation(key), null, List.of());
        }
        Distractor d = findDistractor(key, s -> s.equals(optionId));
        return incorrect(key, max, d);
    }

    private static Result checkNumeric(AnswerKey key, double max, String value) {
        Rational expected = MathEngine.normalise(key.answer()).asPolynomial().constantValue();
        MathEngine.Normalised given = MathEngine.normalise(value);
        if (!given.isPolynomial() || !given.asPolynomial().isConstant()) {
            return new Result(Status.INVALID_INPUT, 0, max, Misconception.TECHNICAL,
                    "Очаква се число, например 2,5 или −3/4.", null, List.of());
        }
        Rational got = given.asPolynomial().constantValue();
        if (got.equals(expected)) {
            return new Result(Status.CORRECT, max, max, null, confirmation(key), null, List.of());
        }
        Distractor d = findDistractor(key, s -> safeEquivalent(s, value));
        if (d == null && got.equals(expected.negate())) {
            d = new Distractor(null, Misconception.SIGN, "Получи същото число, но с обратен знак. Провери знаците.");
        }
        if (d == null) {
            d = new Distractor(null, key.defaultMisconception(),
                    "Получи " + got.display() + ", а верният отговор е " + expected.display()
                            + ". Сравни пресмятанията си с решението стъпка по стъпка — грешката обикновено е в знак или в реда на действията.");
        }
        return incorrect(key, max, d);
    }

    private static Result checkExpression(AnswerKey key, String expected, AnswerForm form, double max, String value) {
        boolean equivalent = MathEngine.equivalent(expected, value);
        if (equivalent) {
            if (form == AnswerForm.NORMAL_FORM && !MathEngine.isNormalForm(value)) {
                return new Result(Status.PARTIAL, round(max / 2), max, Misconception.LIKE_TERMS,
                        "Изразът е равен на верния, но не е в нормален вид. Разкрий скобите и приведи подобните едночлени.",
                        null, List.of());
            }
            if (form == AnswerForm.FACTORISED && !MathEngine.isFullyFactorised(value)) {
                return new Result(Status.PARTIAL, round(max / 2), max, Misconception.FACTORISATION,
                        "Изразът е равен на верния, но не е разложен напълно. Провери за общ множител или формула.",
                        null, List.of());
            }
            return new Result(Status.CORRECT, max, max, null, confirmation(key), null, List.of());
        }
        Distractor d = findDistractor(key, s -> safeEquivalent(s, value));
        if (d == null && safeEquivalent(expected, "-(" + value + ")")) {
            d = new Distractor(null, Misconception.SIGN, "Отговорът ти е с обратен знак. Провери знака пред скобите.");
        }
        if (d == null) {
            String ce = MathEngine.counterexample(expected, value);
            d = new Distractor(null, key.defaultMisconception(), ce == null
                    ? "Изразът ти не е равен на верния. Сравни го с решението стъпка по стъпка."
                    : "Изразът ти не е равен на верния: " + ce + ". Сравни преобразуванията си с решението стъпка по стъпка.");
        }
        return incorrect(key, max, d);
    }

    /**
     * Ordered transformations. Full credit: every step valid and the last step matches the key in
     * the required form. Otherwise credit for the valid prefix, capped at half the points.
     */
    private static Result checkSteps(AnswerKey key, double max, List<String> steps) {
        List<String> nonEmpty = steps == null ? List.of() : steps.stream().filter(s -> !blank(s)).toList();
        if (nonEmpty.isEmpty()) {
            return new Result(Status.UNANSWERED, 0, max, null, "Няма отговор.", null, List.of());
        }
        // The chain starts from the expression in the key's first part, if given, else from step 1.
        List<String> chain = new ArrayList<>();
        if (key.parts() != null && !key.parts().isEmpty() && key.parts().get(0).id().equals("start")) {
            chain.add(key.parts().get(0).answer());
        }
        chain.addAll(nonEmpty);
        MathEngine.StepCheck sc = MathEngine.checkSteps(chain);
        String last = nonEmpty.get(nonEmpty.size() - 1);
        Result finalCheck = checkExpression(key, key.answer(), key.formOrAny(), max, last);
        int offset = chain.size() - nonEmpty.size();
        if (sc.allValid() && finalCheck.status() == Status.CORRECT) {
            return new Result(Status.CORRECT, max, max, null, confirmation(key), null, List.of());
        }
        if (!sc.allValid()) {
            int studentStep = sc.firstInvalidStep() - offset + 1;
            int validSteps = Math.max(0, studentStep - 1);
            double pts = round(Math.min(max / 2, max * validSteps / Math.max(1.0, nonEmpty.size() + 1)));
            Distractor d = findDistractor(key, s -> safeEquivalent(s, nonEmpty.get(Math.max(0, studentStep - 1))));
            Misconception m = d != null ? d.misconception()
                    : key.defaultMisconception() != null ? key.defaultMisconception() : Misconception.TECHNICAL;
            String msg = "Грешката е на ред " + studentStep + ": този ред не е равен на предишния. "
                    + (d != null ? d.explanation() : "Провери този преход стъпка по стъпка.");
            return new Result(pts > 0 ? Status.PARTIAL : Status.INCORRECT, pts, max, m, msg, studentStep, List.of());
        }
        // All steps valid but not finished or not in the required form.
        return new Result(Status.PARTIAL, round(max / 2), max,
                finalCheck.misconception() != null ? finalCheck.misconception() : key.defaultMisconception(),
                "Всички преходи са верни, но решението не е довършено. " + finalCheck.message(), null, List.of());
    }

    private static Result checkStructured(AnswerKey key, double max, Map<String, String> answers) {
        List<PartResult> results = new ArrayList<>();
        double total = 0;
        Misconception firstMisconception = null;
        boolean needsReview = false;
        for (PartKey part : key.parts()) {
            String given = answers == null ? null : answers.get(part.id());
            AnswerPayload pp = new AnswerPayload(given, given, null, null, given);
            AnswerKey partKey = new AnswerKey(part.answer(), part.answer(), part.form(), null,
                    key.distractors(), key.defaultMisconception(), "Вярно.", null, null);
            Result r = switch (part.type()) {
                case NUMERIC -> blank(given) ? unanswered(part.points()) : checkNumeric(partKey, part.points(), given);
                case EXPRESSION -> blank(given) ? unanswered(part.points())
                        : checkExpressionSafe(partKey, part, given);
                case SINGLE_CHOICE -> blank(given) ? unanswered(part.points()) : checkChoice(partKey, part.points(), given);
                default -> new Result(Status.NEEDS_REVIEW, 0, part.points(), null, "За преглед от учител.", null, List.of());
            };
            if (r.status() == Status.NEEDS_REVIEW) needsReview = true;
            if (firstMisconception == null && r.misconception() != null) firstMisconception = r.misconception();
            total += r.points();
            results.add(new PartResult(part.id(), r.status(), r.points(), r.message()));
        }
        total = round(total);
        Status s = needsReview ? Status.NEEDS_REVIEW
                : total >= max ? Status.CORRECT : total > 0 ? Status.PARTIAL : Status.INCORRECT;
        String msg = switch (s) {
            case CORRECT -> confirmation(key);
            case NEEDS_REVIEW -> "Част от отговора ще бъде прегледана от учител.";
            case PARTIAL -> "Частично вярно: получаваш " + fmt(total) + " от " + fmt(max) + " точки. Виж коя част има нужда от поправка.";
            default -> "Нито една част не е вярна засега. Виж решението стъпка по стъпка.";
        };
        return new Result(s, total, max, s == Status.CORRECT ? null : firstMisconception, msg, null, results);
    }

    private static Result checkExpressionSafe(AnswerKey partKey, PartKey part, String given) {
        try {
            return checkExpression(partKey, part.answer(), part.form() == null ? AnswerForm.ANY : part.form(),
                    part.points(), given);
        } catch (MathInputException e) {
            return new Result(Status.INVALID_INPUT, 0, part.points(), Misconception.TECHNICAL, e.getMessage(), null, List.of());
        }
    }

    private static Result unanswered(double max) {
        return new Result(Status.UNANSWERED, 0, max, null, "Няма отговор.", null, List.of());
    }

    private static Result incorrect(AnswerKey key, double max, Distractor d) {
        Misconception m = d != null && d.misconception() != null ? d.misconception()
                : key.defaultMisconception() != null ? key.defaultMisconception() : Misconception.REASONING;
        String msg = d != null && d.explanation() != null ? d.explanation()
                : "Отговорът не съвпада с верния. Виж подсказките или решението и опитай подобна задача.";
        return new Result(Status.INCORRECT, 0, max, m, msg, null, List.of());
    }

    private static Distractor findDistractor(AnswerKey key, java.util.function.Predicate<String> matches) {
        if (key.distractors() == null) return null;
        for (Distractor d : key.distractors()) {
            if (d.match() != null && matches.test(d.match())) return d;
        }
        return null;
    }

    private static boolean safeEquivalent(String a, String b) {
        try {
            return MathEngine.equivalent(a, b);
        } catch (MathInputException e) {
            return false;
        }
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static String fmt(double v) {
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v)).replace('.', ',');
    }
}
