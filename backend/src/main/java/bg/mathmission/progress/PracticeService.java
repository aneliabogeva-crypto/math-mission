package bg.mathmission.progress;

import bg.mathmission.assessment.AnswerChecker;
import bg.mathmission.assessment.LearningEvents;
import bg.mathmission.assessment.MistakeLinks;
import bg.mathmission.assessment.TestDefinition;
import bg.mathmission.assessment.TestDefinitionRepository;
import bg.mathmission.common.ApiException;
import bg.mathmission.content.ContentStatus;
import bg.mathmission.content.Question;
import bg.mathmission.content.QuestionModel;
import bg.mathmission.content.QuestionModel.AnswerPayload;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.QuestionRepository;
import bg.mathmission.content.StudentQuestionView;
import bg.mathmission.curriculum.CurriculumCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ungraded practice: lesson items, knowledge checks and "similar question" follow-ups. Every
 * response gets explanatory feedback (US-STU-07); nothing here produces a school grade.
 */
@Service
public class PracticeService {

    private final QuestionRepository questions;
    private final PracticeResponseRepository practice;
    private final ApplicationEventPublisher events;
    private final TestDefinitionRepository tests;

    public PracticeService(QuestionRepository questions, PracticeResponseRepository practice,
                           ApplicationEventPublisher events, TestDefinitionRepository tests) {
        this.questions = questions;
        this.practice = practice;
        this.events = events;
        this.tests = tests;
    }

    /**
     * Keys of questions used in published assessed tests. Practice never serves them, so a test's
     * answer key cannot leak through practice feedback before the test's review moment.
     */
    Set<String> reservedForTests() {
        List<UUID> ids = tests.findByStatus(TestDefinition.Status.PUBLISHED).stream()
                .flatMap(t -> t.questionIds().stream()).toList();
        return questions.findAllById(ids).stream().map(Question::getQuestionKey).collect(Collectors.toSet());
    }

    public record Feedback(
            String questionKey,
            AnswerChecker.Status status,
            boolean correct,
            String message,
            String misconception,
            String misconceptionLabel,
            Integer errorStep,
            List<AnswerChecker.PartResult> parts,
            String correctAnswer,
            String solution,
            String theoryLessonKey,
            String theoryLessonTitle,
            String similarQuestionKey,
            boolean correctedMistake) {}

    @Transactional(readOnly = true)
    public StudentQuestionView question(String key) {
        return StudentQuestionView.of(published(key));
    }

    @Transactional(readOnly = true)
    public List<StudentQuestionView> questions(List<String> keys) {
        Map<String, Question> byKey = questions.findByQuestionKeyInAndStatus(keys, ContentStatus.PUBLISHED).stream()
                .collect(Collectors.toMap(Question::getQuestionKey, q -> q));
        return keys.stream().filter(byKey::containsKey).map(k -> StudentQuestionView.of(byKey.get(k))).toList();
    }

    /** Graduated hints: level n is only returned after level n-1 (the client keeps the count). */
    @Transactional(readOnly = true)
    public String hint(String key, int level) {
        List<String> hints = published(key).hints();
        if (level < 1 || level > hints.size()) throw ApiException.badRequest("HINT_LEVEL", "Няма такава подсказка.");
        return hints.get(level - 1);
    }

    @Transactional
    public Feedback check(UUID studentId, String key, AnswerPayload payload, int hintsUsed, String requestId, String lessonKey) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw ApiException.badRequest("REQUEST_ID", "Липсва идентификатор на заявката.");
        }
        Question q = published(key);
        AnswerChecker.Result r = AnswerChecker.check(q.getResponseType(), q.key(), q.getMaxPoints(), payload);
        var existing = practice.findByRequestId(requestId);
        boolean corrected;
        if (existing.isPresent()) {
            if (!existing.get().getStudentId().equals(studentId)) throw ApiException.forbidden("Невалидна заявка.");
            corrected = existing.get().isCorrectsPrevious(); // idempotent replay: same feedback, no new points
        } else if (r.status() == AnswerChecker.Status.INVALID_INPUT || r.status() == AnswerChecker.Status.UNANSWERED) {
            corrected = false; // technical input problems are not recorded as mistakes
        } else {
            int hints = Math.max(0, Math.min(3, hintsUsed));
            List<PracticeResponse> earlier = practice.findByStudentIdAndQuestionIdIn(studentId,
                    questions.findByQuestionKeyOrderByVersionDesc(key).stream().map(Question::getId).toList());
            boolean hadMistake = earlier.stream().anyMatch(p -> !p.isCorrect());
            boolean alreadyFixed = earlier.stream().anyMatch(PracticeResponse::isCorrectsPrevious);
            corrected = r.isCorrect() && hadMistake && !alreadyFixed;
            practice.save(new PracticeResponse(requestId, studentId, q.getId(), lessonKey, r.isCorrect(), hints,
                    r.misconception(), corrected));
            events.publishEvent(new LearningEvents.AnswerScored(studentId, q.getSkill(), key, r.isCorrect(), hints,
                    r.misconception(), LearningEvents.Source.PRACTICE, requestId, corrected));
        }
        return feedback(q, r, corrected, studentId);
    }

    private Feedback feedback(Question q, AnswerChecker.Result r, boolean corrected, UUID studentId) {
        QuestionModel.AnswerKey k = q.key();
        var lesson = CurriculumCatalog.lessonForSkill(q.getSkill());
        String msg = r.message();
        if (corrected) msg = msg + " Поправи грешка, която беше допуснал(а) по-рано — точно така се учи!";
        boolean showMethod = r.status() != AnswerChecker.Status.INVALID_INPUT && r.status() != AnswerChecker.Status.UNANSWERED;
        return new Feedback(q.getQuestionKey(), r.status(), r.isCorrect(), msg,
                r.misconception() == null ? null : r.misconception().name(),
                r.misconception() == null ? null : r.misconception().labelBg,
                r.errorStep(), r.parts(),
                showMethod ? correctAnswer(q) : null,
                showMethod ? k.solution() : null,
                lesson.map(CurriculumCatalog.LessonStop::key).orElse(null),
                lesson.map(CurriculumCatalog.LessonStop::title).orElse(null),
                r.isCorrect() ? null : similar(q, studentId),
                corrected);
    }

    private static String correctAnswer(Question q) {
        QuestionModel.AnswerKey k = q.key();
        return switch (q.getResponseType()) {
            case SINGLE_CHOICE -> q.prompt().options().stream().filter(o -> o.id().equals(k.correctOptionId()))
                    .map(QuestionModel.Option::text).findFirst().orElse(null);
            case STRUCTURED -> k.parts().stream().map(p -> p.id() + ": " + p.answer().replace('.', ',')).collect(Collectors.joining("; "));
            case FREE_TEXT -> null;
            default -> k.answer().replace('.', ',');
        };
    }

    /** A new, similar question: same skill, preferably not yet answered by this student. */
    String similar(Question q, UUID studentId) {
        Set<String> reserved = reservedForTests();
        List<Question> candidates = questions.findByStatusAndSkill(ContentStatus.PUBLISHED, q.getSkill()).stream()
                .filter(c -> !c.getQuestionKey().equals(q.getQuestionKey()) && !reserved.contains(c.getQuestionKey())).toList();
        if (candidates.isEmpty()) return null;
        Set<UUID> seen = practice.findByStudentIdAndQuestionIdIn(studentId, candidates.stream().map(Question::getId).toList())
                .stream().map(PracticeResponse::getQuestionId).collect(Collectors.toSet());
        return candidates.stream()
                .sorted(Comparator.comparing((Question c) -> seen.contains(c.getId()))
                        .thenComparing(c -> c.getMisconception() == q.getMisconception() ? 0 : 1)
                        .thenComparing(Question::getQuestionKey))
                .map(Question::getQuestionKey).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ mistake review (US-STU-09)

    public record MistakeGroup(String code, String label, int open, int corrected, String lessonKey, String lessonTitle,
                               List<String> practiceKeys) {}

    @Transactional(readOnly = true)
    public List<MistakeGroup> mistakes(UUID studentId, List<Misconception> testMisconceptions) {
        List<PracticeResponse> all = practice.findByStudentIdOrderByCreatedAtAsc(studentId);
        Map<UUID, Question> qs = questions.findAllById(all.stream().map(PracticeResponse::getQuestionId).distinct().toList())
                .stream().collect(Collectors.toMap(Question::getId, x -> x));
        Map<Misconception, int[]> counts = new LinkedHashMap<>();
        Map<Misconception, Set<String>> skills = new LinkedHashMap<>();
        Map<String, Boolean> fixedKeys = new LinkedHashMap<>();
        for (PracticeResponse p : all) {
            Question q = qs.get(p.getQuestionId());
            if (p.isCorrectsPrevious()) fixedKeys.put(q.getQuestionKey(), true);
        }
        for (PracticeResponse p : all) {
            if (p.isCorrect() || p.getMisconception() == null) continue;
            Question q = qs.get(p.getQuestionId());
            int[] c = counts.computeIfAbsent(p.getMisconception(), m -> new int[2]);
            if (fixedKeys.containsKey(q.getQuestionKey())) c[1]++; else c[0]++;
            skills.computeIfAbsent(p.getMisconception(), m -> new java.util.LinkedHashSet<>()).add(q.getSkill());
        }
        for (Misconception m : testMisconceptions) {
            counts.computeIfAbsent(m, x -> new int[2])[0]++;
        }
        List<MistakeGroup> groups = new ArrayList<>();
        for (var e : counts.entrySet()) {
            String lessonKey = MistakeLinks.lessonFor(e.getKey());
            Set<String> reserved = reservedForTests();
            List<String> practiceKeys = questions.findByStatus(ContentStatus.PUBLISHED).stream()
                    .filter(q -> !reserved.contains(q.getQuestionKey()))
                    .filter(q -> q.getMisconception() == e.getKey()
                            || skills.getOrDefault(e.getKey(), Set.of()).contains(q.getSkill()))
                    .map(Question::getQuestionKey).sorted().limit(5).toList();
            groups.add(new MistakeGroup(e.getKey().name(), e.getKey().labelBg, e.getValue()[0], e.getValue()[1], lessonKey,
                    CurriculumCatalog.lesson(lessonKey).map(CurriculumCatalog.LessonStop::title).orElse(lessonKey), practiceKeys));
        }
        groups.sort(Comparator.comparingInt(MistakeGroup::open).reversed());
        return groups;
    }

    private Question published(String key) {
        if (reservedForTests().contains(key)) throw ApiException.notFound("Задача");
        return questions.findByQuestionKeyAndStatus(key, ContentStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Задача"));
    }
}
