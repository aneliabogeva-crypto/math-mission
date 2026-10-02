package bg.mathmission.assessment;

import bg.mathmission.audit.AuditService;
import bg.mathmission.common.ApiException;
import bg.mathmission.common.AppProperties;
import bg.mathmission.common.Codes;
import bg.mathmission.content.ContentStatus;
import bg.mathmission.content.LessonRepository;
import bg.mathmission.content.Question;
import bg.mathmission.content.QuestionModel.ResponseType;
import bg.mathmission.content.QuestionRepository;
import bg.mathmission.content.StudentQuestionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test assembly and publication rules shared by seeded tests and the teacher test generator
 * (US-TCH-04): at least 20 reviewed questions, no duplicates, full preview with answer key for
 * staff only, and blueprints that must be approved before use.
 */
@Service
public class TestAuthoringService {

    private final TestDefinitionRepository tests;
    private final TestBlueprintRepository blueprints;
    private final QuestionRepository questions;
    private final LessonRepository lessons;
    private final AuditService audit;
    private final AppProperties props;

    public TestAuthoringService(TestDefinitionRepository tests, TestBlueprintRepository blueprints,
                                QuestionRepository questions, LessonRepository lessons, AuditService audit, AppProperties props) {
        this.tests = tests;
        this.blueprints = blueprints;
        this.questions = questions;
        this.lessons = lessons;
        this.audit = audit;
        this.props = props;
    }

    public record GenerateRequest(String title, List<String> paths, List<String> skills, int questionCount,
                                  Map<ResponseType, Integer> typeMix, Map<Question.Difficulty, Integer> difficultyPercent,
                                  int timeLimitMin, TestDefinition.HintPolicy hintPolicy,
                                  TestDefinition.ReviewMoment reviewMoment, GradingScale grading, Long seed) {}

    public record PreviewItem(int position, StudentQuestionView question, String correctAnswer, String solution,
                              String difficulty, List<String> hints) {}

    public record Preview(UUID testId, String key, String title, TestDefinition.Status status, int questionCount,
                          int timeLimitMin, double maxPoints, List<GradingScale.Band> grading,
                          TestDefinition.HintPolicy hintPolicy, TestDefinition.ReviewMoment reviewMoment,
                          Map<String, Long> typeComposition, Map<String, Long> difficultyComposition,
                          List<String> warnings, List<PreviewItem> items) {}

    /** Builds a draft teacher test from the reviewed question bank. Nothing is visible to students yet. */
    @Transactional
    public Preview generate(UUID teacherId, GenerateRequest r) {
        if (r.questionCount() < TestDefinition.MIN_ASSESSED_QUESTIONS) {
            throw ApiException.badRequest("TOO_FEW", "Оценяващият тест трябва да има поне 20 въпроса.");
        }
        if (r.questionCount() > 60) throw ApiException.badRequest("TOO_MANY", "Най-много 60 въпроса.");
        Set<String> lessonKeys = lessons.findByStatus(ContentStatus.PUBLISHED).stream()
                .flatMap(l -> l.content().allQuestionKeys().stream()).collect(Collectors.toSet());
        List<Question> pool = questions.findByStatus(ContentStatus.PUBLISHED).stream()
                .filter(q -> r.paths() == null || r.paths().isEmpty() || r.paths().contains(q.getPath()))
                .filter(q -> r.skills() == null || r.skills().isEmpty() || r.skills().contains(q.getSkill()))
                .filter(q -> q.getResponseType() != ResponseType.FREE_TEXT || (r.typeMix() != null && r.typeMix().containsKey(ResponseType.FREE_TEXT)))
                .filter(q -> !lessonKeys.contains(q.getQuestionKey())) // lesson items stay as open practice
                .sorted(Comparator.comparing(Question::getQuestionKey))
                .toList();
        List<String> warnings = new ArrayList<>();
        Random rnd = new Random(r.seed() == null ? System.nanoTime() : r.seed());
        List<Question> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, rnd);

        // Pick by response-type quota first, then fill; never the same question twice.
        List<Question> chosen = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        Set<String> usedPrompts = new HashSet<>();
        if (r.typeMix() != null) {
            for (var e : r.typeMix().entrySet()) {
                int need = e.getValue();
                for (Question q : orderByDifficulty(shuffled, r.difficultyPercent(), need)) {
                    if (need == 0) break;
                    if (q.getResponseType() == e.getKey() && add(q, chosen, usedKeys, usedPrompts)) need--;
                }
                if (need > 0) warnings.add("Няма достатъчно въпроси от тип " + e.getKey() + " (липсват " + need + ").");
            }
        }
        for (Question q : shuffled) {
            if (chosen.size() >= r.questionCount()) break;
            add(q, chosen, usedKeys, usedPrompts);
        }
        if (chosen.size() < r.questionCount()) {
            throw ApiException.conflict("POOL_TOO_SMALL", "В банката има само " + chosen.size()
                    + " подходящи прегледани въпроса за избраните теми. Разширете темите или намалете броя (минимум 20).");
        }
        chosen = chosen.subList(0, r.questionCount());
        String path = r.paths() != null && r.paths().size() == 1 ? r.paths().get(0) : "D";
        TestDefinition t = tests.save(new TestDefinition("T-" + Codes.generate(8),
                r.title() == null || r.title().isBlank() ? "Тест от учителя" : r.title().trim(),
                TestDefinition.Kind.TEACHER, path, null, Math.max(10, r.timeLimitMin()),
                r.grading() == null ? GradingScale.defaultScale() : r.grading(),
                chosen.stream().map(Question::getId).toList(),
                r.hintPolicy() == null ? TestDefinition.HintPolicy.NONE : r.hintPolicy(),
                r.reviewMoment() == null ? TestDefinition.ReviewMoment.AFTER_SUBMIT : r.reviewMoment(),
                teacherId, props.academicYear()));
        audit.record(teacherId, "TEST_GENERATED", "TestDefinition", t.getId(), chosen.size() + " questions");
        return preview(t, warnings);
    }

    private static List<Question> orderByDifficulty(List<Question> pool, Map<Question.Difficulty, Integer> pct, int n) {
        if (pct == null || pct.isEmpty()) return pool;
        // Interleave difficulties according to the requested percentages.
        Map<Question.Difficulty, List<Question>> byDiff = pool.stream().collect(Collectors.groupingBy(Question::getDifficulty,
                LinkedHashMap::new, Collectors.toList()));
        List<Question> out = new ArrayList<>();
        for (var e : pct.entrySet()) {
            int take = (int) Math.round(n * e.getValue() / 100.0);
            out.addAll(byDiff.getOrDefault(e.getKey(), List.of()).stream().limit(Math.max(take, 0)).toList());
        }
        pool.stream().filter(q -> !out.contains(q)).forEach(out::add);
        return out;
    }

    private static boolean add(Question q, List<Question> chosen, Set<String> keys, Set<String> prompts) {
        if (keys.contains(q.getQuestionKey()) || prompts.contains(q.fingerprint())) return false;
        keys.add(q.getQuestionKey());
        prompts.add(q.fingerprint());
        chosen.add(q);
        return true;
    }

    /** Full preview including the answer key: only ever returned to staff endpoints. */
    @Transactional(readOnly = true)
    public Preview preview(UUID testId) {
        return preview(tests.findById(testId).orElseThrow(() -> ApiException.notFound("Тест")), List.of());
    }

    Preview preview(TestDefinition t, List<String> warnings) {
        Map<UUID, Question> qs = questions.findAllById(t.questionIds()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<PreviewItem> items = new ArrayList<>();
        int i = 0;
        for (UUID id : t.questionIds()) {
            Question q = qs.get(id);
            items.add(new PreviewItem(i++, StudentQuestionView.of(q), AssessmentService.correctAnswerText(q),
                    q.key().solution(), q.getDifficulty().name(), q.hints()));
        }
        List<Question> list = t.questionIds().stream().map(qs::get).toList();
        return new Preview(t.getId(), t.getTestKey(), t.getTitle(), t.getStatus(), list.size(), t.getTimeLimitMin(),
                list.stream().mapToDouble(Question::getMaxPoints).sum(), t.grading().bands(), t.getHintPolicy(),
                t.getReviewMoment(),
                list.stream().collect(Collectors.groupingBy(q -> q.getResponseType().name(), LinkedHashMap::new, Collectors.counting())),
                list.stream().collect(Collectors.groupingBy(q -> q.getDifficulty().name(), LinkedHashMap::new, Collectors.counting())),
                warnings, items);
    }

    /** Publication gate for every assessed test. */
    @Transactional
    public Preview publish(UUID actorId, UUID testId, boolean actorIsAdmin) {
        TestDefinition t = tests.findById(testId).orElseThrow(() -> ApiException.notFound("Тест"));
        if (!actorIsAdmin && !t.getCreatedBy().equals(actorId)) throw ApiException.forbidden("Тестът не е ваш.");
        validateForPublication(t);
        t.setStatus(TestDefinition.Status.PUBLISHED);
        audit.record(actorId, "TEST_PUBLISHED", "TestDefinition", t.getId(), t.getTestKey());
        return preview(t, List.of());
    }

    public void validateForPublication(TestDefinition t) {
        List<UUID> ids = t.questionIds();
        if (ids.size() < TestDefinition.MIN_ASSESSED_QUESTIONS) {
            throw ApiException.badRequest("TOO_FEW", "Не може да се публикува оценяващ тест с по-малко от 20 въпроса.");
        }
        if (t.getKind() == TestDefinition.Kind.INTEGRATED && ids.size() != 24) {
            throw ApiException.badRequest("BLUEPRINT", "Обобщителният тест трябва да има 24 въпроса.");
        }
        List<Question> qs = questions.findAllById(ids);
        if (qs.size() != ids.size() || qs.stream().anyMatch(q -> q.getStatus() != ContentStatus.PUBLISHED)) {
            throw ApiException.badRequest("NOT_REVIEWED", "Всички въпроси трябва да са прегледани и публикувани.");
        }
        if (new HashSet<>(ids).size() != ids.size()
                || qs.stream().map(Question::getQuestionKey).distinct().count() != qs.size()
                || qs.stream().map(Question::fingerprint).distinct().count() != qs.size()) {
            throw ApiException.badRequest("DUPLICATES", "Тестът съдържа повтарящи се въпроси.");
        }
        if (t.getBlueprintId() != null) {
            TestBlueprint b = blueprints.findById(t.getBlueprintId()).orElseThrow();
            if (b.getStatus() != TestBlueprint.Status.APPROVED) {
                throw ApiException.badRequest("BLUEPRINT_NOT_APPROVED", "Моделът на теста не е одобрен.");
            }
            Map<String, Long> actual = qs.stream().collect(Collectors.groupingBy(q -> group(q.getResponseType()), Collectors.counting()));
            for (var e : b.composition().entrySet()) {
                if (actual.getOrDefault(e.getKey(), 0L) != e.getValue().longValue()) {
                    throw ApiException.badRequest("BLUEPRINT", "Съставът не отговаря на модела: " + e.getKey()
                            + " трябва да е " + e.getValue() + ", а е " + actual.getOrDefault(e.getKey(), 0L) + ".");
                }
            }
        }
    }

    /** Blueprint groups: multiple choice, short answer (numeric/expression), multi-step (steps/structured/free text). */
    public static String group(ResponseType t) {
        return switch (t) {
            case SINGLE_CHOICE -> "MULTIPLE_CHOICE";
            case NUMERIC, EXPRESSION -> "SHORT_ANSWER";
            case STEPS, STRUCTURED, FREE_TEXT -> "MULTI_STEP";
        };
    }
}
