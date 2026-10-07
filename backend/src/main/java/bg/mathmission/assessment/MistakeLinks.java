package bg.mathmission.assessment;

import bg.mathmission.content.QuestionModel.Misconception;

/** Where to send a student for each misconception category (theory or practice). */
public final class MistakeLinks {

    private MistakeLinks() {}

    public static String lessonFor(Misconception m) {
        return switch (m) {
            case SIGN -> "C3";
            case BRACKETS -> "C4";
            case ORDER_OF_OPERATIONS -> "A4";
            case LIKE_TERMS -> "B3";
            case FORMULA_APPLICATION -> "C6";
            case FACTORISATION -> "C11";
            case REASONING -> "A5";
            case TECHNICAL -> "A3";
        };
    }
}
