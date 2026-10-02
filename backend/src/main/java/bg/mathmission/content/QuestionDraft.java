package bg.mathmission.content;

import bg.mathmission.content.QuestionModel.AnswerKey;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.QuestionModel.Prompt;
import bg.mathmission.content.QuestionModel.ResponseType;
import java.util.List;

/** Authoring payload for a question version. */
public record QuestionDraft(
        String path,
        String skill,
        String learningOutcome,
        ResponseType responseType,
        Question.Difficulty difficulty,
        int estimatedSeconds,
        double maxPoints,
        Misconception misconception,
        Prompt prompt,
        AnswerKey key,
        List<String> hints,
        String sourceDeclaration) {}
