package bg.mathmission.assessment;

import bg.mathmission.content.QuestionModel.Misconception;
import java.util.UUID;

/** Domain events published by the assessment module and consumed by progress and gamification. */
public final class LearningEvents {

    private LearningEvents() {}

    public enum Source { PRACTICE, TEST }

    /** One scored answer. {@code correctsPrevious} is true when it fixes an earlier mistake on the same skill item. */
    public record AnswerScored(UUID studentId, String skill, String questionKey, boolean correct, int hintsUsed,
                               Misconception misconception, Source source, String sourceRef, boolean correctsPrevious) {}

    public record TestSubmitted(UUID studentId, UUID attemptId, String testTitle, double percent) {}

    public record LessonCompleted(UUID studentId, String lessonKey) {}
}
