package bg.mathmission.content;

import bg.mathmission.content.QuestionModel.Prompt;
import bg.mathmission.content.QuestionModel.ResponseType;
import java.util.UUID;

/**
 * The only representation of a question that is sent to a student before the review moment:
 * no answer key, no solution, no distractor explanations, no hint text.
 */
public record StudentQuestionView(
        UUID id,
        String key,
        String skill,
        String skillTitle,
        ResponseType responseType,
        Prompt prompt,
        double maxPoints,
        int estimatedSeconds,
        int hintLevels,
        String difficulty) {

    public static StudentQuestionView of(Question q) {
        return new StudentQuestionView(q.getId(), q.getQuestionKey(), q.getSkill(),
                bg.mathmission.curriculum.CurriculumCatalog.skillTitle(q.getSkill()), q.getResponseType(),
                q.prompt(), q.getMaxPoints(), q.getEstimatedSeconds(), q.hints().size(), q.getDifficulty().name());
    }
}
