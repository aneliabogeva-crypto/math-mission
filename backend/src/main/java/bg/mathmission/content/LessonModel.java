package bg.mathmission.content;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured lesson document following the mandatory 12-step pattern (spec 4.2). Question
 * references are question keys; the student client receives only their safe prompts.
 */
public record LessonModel(
        Section context,              // 1. real-life context or visual puzzle
        List<String> objectives,      // 2. "After this lesson, you will be able to..."
        QuestionSet prerequisiteCheck,// 3. prerequisite check
        List<String> explanation,     // 4. conversational explanation
        Section definition,           // 5. formal definition card
        Visual visual,                // 6. visual interpretation / short animation
        List<WorkedExample> workedExamples, // 7 + 8. examples with "why" for each step
        List<WatchOut> watchOut,      // 9. common mistakes
        List<GuidedItem> guidedPractice, // 10. guided practice, reducing support
        QuestionSet check,            // 11. ungraded check, 3 to 7 items
        List<String> summary,         // 12. summary
        NextStep nextStep) {          // 12. recommended next step

    public record Section(String title, String text) {}

    public record QuestionSet(String intro, List<String> questionKeys) {}

    /** A visual described by structured data and fully described in text for screen readers. */
    public record Visual(String kind, String title, String description, List<VisualGroup> groups) {}

    public record VisualGroup(String label, List<String> items, String colour) {}

    public enum Level { BASIC, TYPICAL, CHALLENGE }

    public record WorkedExample(Level level, String problem, List<Step> steps, String answer) {}

    public record Step(String expression, String why) {}

    public record WatchOut(String wrong, String right, String why) {}

    public enum Support { FULL, PARTIAL, NONE }

    public record GuidedItem(String questionKey, Support support, String coaching) {}

    public record NextStep(String lessonKey, String text) {}

    /** Returns the list of pattern violations; empty means the lesson may be submitted for review. */
    public List<String> validatePattern() {
        List<String> errors = new ArrayList<>();
        if (context == null || blank(context.text())) errors.add("1. Липсва реален контекст или пъзел.");
        if (objectives == null || objectives.isEmpty()) errors.add("2. Липсва цел „След този урок ще можеш да…“.");
        if (prerequisiteCheck == null || prerequisiteCheck.questionKeys() == null || prerequisiteCheck.questionKeys().isEmpty())
            errors.add("3. Липсва проверка на предварителните знания.");
        if (explanation == null || explanation.isEmpty()) errors.add("4. Липсва обяснение.");
        if (definition == null || blank(definition.text())) errors.add("5. Липсва формална дефиниция.");
        if (visual == null || blank(visual.description())) errors.add("6. Липсва визуализация с текстово описание.");
        if (workedExamples == null || workedExamples.size() < 3) {
            errors.add("7. Нужни са поне три решени примера.");
        } else {
            List<Level> levels = workedExamples.stream().map(WorkedExample::level).toList();
            if (!levels.contains(Level.BASIC) || !levels.contains(Level.TYPICAL) || !levels.contains(Level.CHALLENGE))
                errors.add("7. Примерите трябва да са основен, типичен и предизвикателен.");
            for (int i = 0; i < workedExamples.size(); i++) {
                WorkedExample ex = workedExamples.get(i);
                if (ex.steps() == null || ex.steps().isEmpty() || ex.steps().stream().anyMatch(s -> blank(s.why())))
                    errors.add("8. Пример " + (i + 1) + ": всяко преобразувание трябва да има обяснение защо е вярно.");
            }
        }
        if (watchOut == null || watchOut.isEmpty()) errors.add("9. Липсва раздел „Внимание“ с типични грешки.");
        if (guidedPractice == null || guidedPractice.isEmpty()) {
            errors.add("10. Липсва упражнение с насоки.");
        } else if (guidedPractice.get(0).support() != Support.FULL
                || guidedPractice.get(guidedPractice.size() - 1).support() == Support.FULL) {
            errors.add("10. Подкрепата в упражнението трябва постепенно да намалява.");
        }
        if (check == null || check.questionKeys() == null || check.questionKeys().size() < 3 || check.questionKeys().size() > 7)
            errors.add("11. Проверката без оценка трябва да има от 3 до 7 задачи.");
        if (summary == null || summary.isEmpty()) errors.add("12. Липсва обобщение.");
        if (nextStep == null || blank(nextStep.text())) errors.add("12. Липсва препоръчана следваща стъпка.");
        return errors;
    }

    public List<String> allQuestionKeys() {
        List<String> keys = new ArrayList<>();
        if (prerequisiteCheck != null && prerequisiteCheck.questionKeys() != null) keys.addAll(prerequisiteCheck.questionKeys());
        if (guidedPractice != null) guidedPractice.forEach(g -> keys.add(g.questionKey()));
        if (check != null && check.questionKeys() != null) keys.addAll(check.questionKeys());
        return keys;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
