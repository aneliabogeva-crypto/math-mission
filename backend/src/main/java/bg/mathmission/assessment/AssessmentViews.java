package bg.mathmission.assessment;

import bg.mathmission.content.QuestionModel.AnswerPayload;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.StudentQuestionView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** API views for the assessment module. */
public final class AssessmentViews {

    private AssessmentViews() {}

    public record TestSummary(UUID id, String key, String title, TestDefinition.Kind kind, String kindLabel, String path,
                              int questionCount, int timeLimitMin, List<GradingScale.Band> gradingBands,
                              TestDefinition.HintPolicy hintPolicy, String scoringNote, String scopeNote,
                              UUID inProgressAttemptId, Double bestPercent, int completedAttempts) {}

    public record AttemptItem(int position, StudentQuestionView question, AnswerPayload answer, boolean markedForReview,
                              boolean answered, int hintsUsed, List<String> revealedHints, String lastRequestId) {}

    public record AttemptView(UUID attemptId, UUID testId, String title, String kindLabel, TestAttempt.Status status,
                              Instant startedAt, Instant deadlineAt, Instant serverNow, int lastPosition,
                              TestDefinition.HintPolicy hintPolicy, List<AttemptItem> items) {}

    public record SaveAck(int position, Instant savedAt, String requestId, boolean duplicate) {}

    public record SkillResult(String skill, String title, double points, double maxPoints, long percent, String lessonKey) {}

    public record MisconceptionGroup(Misconception code, String label, int count, String lessonKey) {}

    public record ItemReview(int position, StudentQuestionView question, AnswerPayload answer, AnswerChecker.Status status,
                             Double points, double maxPoints, String feedback, String misconception,
                             String correctAnswer, String solution, String theoryLessonKey, String scoredBy,
                             String teacherComment) {}

    public record ResultView(UUID attemptId, String title, String kindLabel, Double points, Double maxPoints, Double percent,
                             Integer grade, String gradeLabel, String disclaimer, TestAttempt.ScoringSource scoringSource,
                             List<SkillResult> skills, List<MisconceptionGroup> misconceptions, boolean answerKeyVisible,
                             List<ItemReview> items, List<String> corrections, Instant submittedAt) {}
}
