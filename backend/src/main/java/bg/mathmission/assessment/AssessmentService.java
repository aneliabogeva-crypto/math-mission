package bg.mathmission.assessment;

import bg.mathmission.assessment.AssessmentViews.AttemptItem;
import bg.mathmission.assessment.AssessmentViews.AttemptView;
import bg.mathmission.assessment.AssessmentViews.ItemReview;
import bg.mathmission.assessment.AssessmentViews.MisconceptionGroup;
import bg.mathmission.assessment.AssessmentViews.ResultView;
import bg.mathmission.assessment.AssessmentViews.SaveAck;
import bg.mathmission.assessment.AssessmentViews.SkillResult;
import bg.mathmission.assessment.AssessmentViews.TestSummary;
import bg.mathmission.audit.AuditService;
import bg.mathmission.common.ApiException;
import bg.mathmission.content.ContentService;
import bg.mathmission.content.ContentStatus;
import bg.mathmission.content.Question;
import bg.mathmission.content.QuestionModel;
import bg.mathmission.content.QuestionModel.AnswerPayload;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.QuestionRepository;
import bg.mathmission.content.StudentQuestionView;
import bg.mathmission.curriculum.CurriculumCatalog;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test attempts: start (idempotent), autosave after every answer (idempotent by request id),
 * submit (idempotent), deterministic scoring, and recalculation after content corrections.
 */
@Service
public class AssessmentService {

    public static final String NOT_OFFICIAL =
            "Това е учебна оценка за подготовка. Тя не е официална училищна или държавна оценка.";
    public static final String NOT_NEA_SIMULATION =
            "Тестът е тематичен (алгебра) и не е пълна симулация на НВО след 7. клас.";

    /** Answers that were confirmed offline may arrive shortly after the timer ends. */
    static final Duration SYNC_GRACE = Duration.ofMinutes(5);

    private final TestDefinitionRepository tests;
    private final TestAttemptRepository attempts;
    private final ItemResponseRepository responses;
    private final CorrectionNoticeRepository notices;
    private final QuestionRepository questions;
    private final AssignmentPolicy assignments;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public AssessmentService(TestDefinitionRepository tests, TestAttemptRepository attempts, ItemResponseRepository responses,
                             CorrectionNoticeRepository notices, QuestionRepository questions,
                             AssignmentPolicy assignments, ApplicationEventPublisher events, AuditService audit) {
        this.tests = tests;
        this.attempts = attempts;
        this.responses = responses;
        this.notices = notices;
        this.questions = questions;
        this.assignments = assignments;
        this.events = events;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ catalogue

    @Transactional(readOnly = true)
    public List<TestSummary> availableTests(UUID studentId) {
        List<TestAttempt> mine = attempts.findByStudentIdOrderByStartedAtDesc(studentId);
        return tests.findByStatus(TestDefinition.Status.PUBLISHED).stream()
                .filter(t -> t.getKind() != TestDefinition.Kind.TEACHER)
                .map(t -> summary(t, mine.stream().filter(a -> a.getTestId().equals(t.getId())).toList()))
                .toList();
    }

    public TestSummary summary(TestDefinition t, List<TestAttempt> mine) {
        UUID inProgress = mine.stream().filter(a -> a.getStatus() == TestAttempt.Status.IN_PROGRESS)
                .map(TestAttempt::getId).findFirst().orElse(null);
        Double best = mine.stream().filter(a -> a.getPercent() != null).map(TestAttempt::getPercent)
                .max(Double::compare).orElse(null);
        return new TestSummary(t.getId(), t.getTestKey(), t.getTitle(), t.getKind(), kindLabel(t.getKind()), t.getPath(),
                t.questionIds().size(), t.getTimeLimitMin(), t.showsGrade() ? t.grading().bands() : null,
                t.getHintPolicy(), t.showsGrade() ? NOT_OFFICIAL : "Резултатът е в точки от 100 и анализ по умения. " + NOT_OFFICIAL,
                NOT_NEA_SIMULATION, inProgress, best, (int) mine.stream().filter(a -> a.getStatus() == TestAttempt.Status.SUBMITTED).count());
    }

    static String kindLabel(TestDefinition.Kind k) {
        return switch (k) {
            case DIAGNOSTIC -> "Диагностичен тест";
            case THEMATIC -> "Тематичен тест";
            case INTEGRATED -> "Обобщителен тест";
            case TEACHER -> "Тест от учителя";
            case ASSESSMENT_STYLE -> "Тест във формат за подготовка";
        };
    }

    // ------------------------------------------------------------------ attempt lifecycle

    @Transactional
    public AttemptView start(UUID studentId, UUID testId, UUID clientAttemptId, UUID assignmentId) {
        UUID attemptId = clientAttemptId != null ? clientAttemptId : UUID.randomUUID();
        Optional<TestAttempt> existing = attempts.findById(attemptId);
        if (existing.isPresent()) {
            TestAttempt a = existing.get();
            if (!a.getStudentId().equals(studentId)) throw ApiException.forbidden("Опитът не е твой.");
            return view(a); // retried start: no duplicate attempt
        }
        TestDefinition t = tests.findById(testId).orElseThrow(() -> ApiException.notFound("Тест"));
        if (t.getStatus() != TestDefinition.Status.PUBLISHED) throw ApiException.notFound("Тест");
        Instant deadline = Instant.now().plus(Duration.ofMinutes(t.getTimeLimitMin()));
        if (assignmentId != null) {
            AssignmentPolicy.Rules r = assignments.rulesFor(assignmentId, studentId)
                    .orElseThrow(() -> ApiException.forbidden("Тази задача не е зададена на теб."));
            if (!"TEST".equals(r.targetType()) || !r.targetKey().equals(t.getTestKey())) {
                throw ApiException.badRequest("ASSIGNMENT_MISMATCH", "Заданието е за друг тест.");
            }
            if (r.deadline() != null && Instant.now().isAfter(r.deadline())) {
                throw ApiException.conflict("ASSIGNMENT_CLOSED", "Срокът на заданието е изтекъл.");
            }
            if (attempts.countByStudentIdAndAssignmentId(studentId, assignmentId) >= r.allowedAttempts()) {
                throw ApiException.conflict("NO_ATTEMPTS_LEFT", "Използва всички разрешени опити за това задание.");
            }
            if (r.deadline() != null && r.deadline().isBefore(deadline)) deadline = r.deadline();
        } else {
            attempts.findByStudentIdAndTestIdOrderByStartedAtAsc(studentId, testId).stream()
                    .filter(a -> a.getStatus() == TestAttempt.Status.IN_PROGRESS && a.getAssignmentId() == null)
                    .findFirst().ifPresent(a -> {
                        throw new ApiException(HttpStatus.CONFLICT, "ATTEMPT_IN_PROGRESS:" + a.getId(),
                                "Вече имаш започнат опит. Продължи от мястото, където спря.");
                    });
        }
        TestAttempt a = attempts.save(new TestAttempt(attemptId, testId, studentId, assignmentId, deadline));
        Map<UUID, Question> byId = questionsById(t.questionIds());
        List<UUID> order = new ArrayList<>(t.questionIds().stream()
                .filter(id -> byId.containsKey(id) && byId.get(id).getStatus() != ContentStatus.WITHDRAWN).toList());
        Collections.shuffle(order, new Random(attemptId.getMostSignificantBits())); // randomised, reproducible per attempt
        for (int i = 0; i < order.size(); i++) {
            responses.save(new ItemResponse(a.getId(), order.get(i), i));
        }
        return view(a);
    }

    @Transactional(readOnly = true)
    public AttemptView get(UUID studentId, UUID attemptId) {
        return view(owned(studentId, attemptId));
    }

    @Transactional
    public SaveAck save(UUID studentId, UUID attemptId, int position, String requestId, AnswerPayload payload,
                        boolean marked, Integer lastPosition) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw ApiException.badRequest("REQUEST_ID", "Липсва идентификатор на заявката.");
        }
        TestAttempt a = owned(studentId, attemptId);
        ItemResponse r = responses.findByAttemptIdAndPosition(attemptId, position)
                .orElseThrow(() -> ApiException.notFound("Въпрос"));
        if (requestId.equals(r.getLastRequestId())) {
            return new SaveAck(position, r.getSavedAt(), requestId, true); // duplicate retry
        }
        if (a.getStatus() == TestAttempt.Status.SUBMITTED) {
            throw ApiException.conflict("ATTEMPT_SUBMITTED", "Тестът вече е предаден.");
        }
        if (a.getDeadlineAt() != null && Instant.now().isAfter(a.getDeadlineAt().plus(SYNC_GRACE))) {
            throw ApiException.conflict("TIME_OVER", "Времето за теста изтече. Предай теста, за да видиш резултата.");
        }
        r.save(payload, marked, requestId);
        if (lastPosition != null) a.setLastPosition(lastPosition);
        return new SaveAck(position, r.getSavedAt(), requestId, false);
    }

    @Transactional
    public String hint(UUID studentId, UUID attemptId, int position, int level) {
        TestAttempt a = owned(studentId, attemptId);
        if (a.getStatus() != TestAttempt.Status.IN_PROGRESS) throw ApiException.conflict("ATTEMPT_SUBMITTED", "Тестът вече е предаден.");
        if (hintPolicy(a) != TestDefinition.HintPolicy.ALLOWED) {
            throw ApiException.forbidden("В този тест подсказките са изключени.");
        }
        ItemResponse r = responses.findByAttemptIdAndPosition(attemptId, position).orElseThrow(() -> ApiException.notFound("Въпрос"));
        List<String> hints = questions.findById(r.getQuestionId()).orElseThrow().hints();
        if (level < 1 || level > hints.size()) throw ApiException.badRequest("HINT_LEVEL", "Няма такава подсказка.");
        if (level > r.getHintsUsed() + 1) throw ApiException.badRequest("HINT_ORDER", "Първо отвори предишната подсказка.");
        r.useHint(level); // recorded for mastery
        return hints.get(level - 1);
    }

    @Transactional
    public ResultView submit(UUID studentId, UUID attemptId) {
        TestAttempt a = owned(studentId, attemptId);
        if (a.getStatus() == TestAttempt.Status.SUBMITTED) {
            return result(a, true); // idempotent
        }
        TestDefinition t = tests.findById(a.getTestId()).orElseThrow();
        a.submit();
        List<ItemResponse> items = responses.findByAttemptIdOrderByPositionAsc(a.getId());
        Map<UUID, Question> qs = questionsById(items.stream().map(ItemResponse::getQuestionId).toList());
        for (ItemResponse r : items) {
            Question q = qs.get(r.getQuestionId());
            if (q.getStatus() == ContentStatus.WITHDRAWN) {
                r.neutralise("Въпросът е оттеглен и не се брои.");
                continue;
            }
            AnswerChecker.Result res = AnswerChecker.check(q.getResponseType(), q.key(), q.getMaxPoints(), r.payload());
            r.score(res, "AUTOMATED");
            events.publishEvent(new LearningEvents.AnswerScored(studentId, q.getSkill(), q.getQuestionKey(),
                    res.isCorrect(), r.getHintsUsed(), res.misconception(), LearningEvents.Source.TEST,
                    a.getId() + ":" + r.getPosition(), false));
        }
        rescoreTotals(a, t, items);
        events.publishEvent(new LearningEvents.TestSubmitted(studentId, a.getId(), t.getTitle(), a.getPercent()));
        return result(a, true);
    }

    @Transactional(readOnly = true)
    public ResultView result(UUID studentId, UUID attemptId) {
        TestAttempt a = owned(studentId, attemptId);
        if (a.getStatus() != TestAttempt.Status.SUBMITTED) throw ApiException.conflict("NOT_SUBMITTED", "Тестът още не е предаден.");
        return result(a, true);
    }

    /** Result for a teacher (or other authorised viewer); the key is always visible to staff. */
    @Transactional(readOnly = true)
    public ResultView resultForStaff(UUID attemptId) {
        TestAttempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Опит"));
        return result(a, false);
    }

    // ------------------------------------------------------------------ teacher review (US-TCH-05)

    public record ReviewQueueItem(UUID itemId, UUID attemptId, UUID studentId, String testTitle, int position,
                                  StudentQuestionView question, AnswerPayload answer, double maxPoints, String rubric,
                                  String solution, Instant submittedAt) {}

    /** Responses that need human judgement, for students the caller is allowed to see. */
    @Transactional(readOnly = true)
    public List<ReviewQueueItem> reviewQueue(java.util.Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) return List.of();
        List<TestAttempt> submitted = studentIds.stream()
                .flatMap(id -> attempts.findByStudentIdOrderByStartedAtDesc(id).stream())
                .filter(a -> a.getStatus() == TestAttempt.Status.SUBMITTED).toList();
        Map<UUID, TestAttempt> byId = submitted.stream().collect(Collectors.toMap(TestAttempt::getId, Function.identity()));
        List<ItemResponse> pending = responses.findByStatusAndAttemptIdIn(AnswerChecker.Status.NEEDS_REVIEW, byId.keySet());
        Map<UUID, Question> qs = questionsById(pending.stream().map(ItemResponse::getQuestionId).toList());
        return pending.stream().map(r -> {
            TestAttempt a = byId.get(r.getAttemptId());
            Question q = qs.get(r.getQuestionId());
            String title = tests.findById(a.getTestId()).map(TestDefinition::getTitle).orElse("");
            return new ReviewQueueItem(r.getId(), a.getId(), a.getStudentId(), title, r.getPosition(), StudentQuestionView.of(q),
                    r.payload(), q.getMaxPoints(), q.key().rubric(), q.key().solution(), a.getSubmittedAt());
        }).sorted(java.util.Comparator.comparing(ReviewQueueItem::submittedAt)).toList();
    }

    /** Teacher awards (partial) points with a comment; the change is audited and the attempt re-totalled. */
    @Transactional
    public ResultView teacherScore(UUID teacherId, java.util.Collection<UUID> allowedStudents, UUID itemId, double points, String comment) {
        ItemResponse r = responses.findById(itemId).orElseThrow(() -> ApiException.notFound("Отговор"));
        TestAttempt a = attempts.findById(r.getAttemptId()).orElseThrow();
        if (!allowedStudents.contains(a.getStudentId())) throw ApiException.forbidden("Ученикът не е във ваш клас.");
        if (a.getStatus() != TestAttempt.Status.SUBMITTED) throw ApiException.conflict("NOT_SUBMITTED", "Опитът още не е предаден.");
        Question q = questions.findById(r.getQuestionId()).orElseThrow();
        if (points < 0 || points > q.getMaxPoints()) {
            throw ApiException.badRequest("POINTS", "Точките трябва да са между 0 и " + q.getMaxPoints() + ".");
        }
        Double old = r.getPoints();
        r.teacherScore(AnswerChecker.round(points), q.getMaxPoints(), teacherId, comment);
        TestDefinition t = tests.findById(a.getTestId()).orElseThrow();
        rescoreTotals(a, t, responses.findByAttemptIdOrderByPositionAsc(a.getId()));
        audit.record(teacherId, "SCORE_CHANGED", "ItemResponse", r.getId(),
                "attempt=" + a.getId() + ";from=" + old + ";to=" + points);
        return result(a, false);
    }

    // ------------------------------------------------------------------ scoring

    void rescoreTotals(TestAttempt a, TestDefinition t, List<ItemResponse> items) {
        Map<UUID, Question> qs = questionsById(items.stream().map(ItemResponse::getQuestionId).toList());
        double pts = 0;
        double max = 0;
        boolean pending = false;
        boolean teacher = false;
        for (ItemResponse r : items) {
            if ("WITHDRAWN".equals(r.getScoredBy())) continue;
            max += qs.get(r.getQuestionId()).getMaxPoints();
            pts += r.getPoints() == null ? 0 : r.getPoints();
            if (r.getStatus() == AnswerChecker.Status.NEEDS_REVIEW) pending = true;
            if ("TEACHER".equals(r.getScoredBy())) teacher = true;
        }
        pts = AnswerChecker.round(pts);
        double percent = max == 0 ? 0 : pts / max * 100;
        if (t.getKind() == TestDefinition.Kind.ASSESSMENT_STYLE) {
            // Points out of 100; no grade conversion is shown in assessment-style mode.
            pts = AnswerChecker.round(percent);
            max = 100;
        }
        Integer grade = t.showsGrade() && !pending ? t.grading().bandFor(percent).grade() : null;
        a.applyScore(pts, max, grade, pending ? TestAttempt.ScoringSource.PENDING_TEACHER_REVIEW
                : teacher ? TestAttempt.ScoringSource.TEACHER_REVIEWED : TestAttempt.ScoringSource.AUTOMATED);
    }

    /**
     * Deterministic recalculation after a question was withdrawn or its key corrected (US-CNT-03).
     * Affected attempts get a transparent correction notice when their result changed.
     */
    @EventListener
    @Transactional
    public void onQuestionChanged(ContentService.QuestionChanged e) {
        if (!e.withdrawn() && e.newQuestionId() != null) {
            tests.findAll().forEach(t -> {
                if (t.questionIds().contains(e.oldQuestionId())) t.replaceQuestion(e.oldQuestionId(), e.newQuestionId());
            });
        }
        List<ItemResponse> affected = responses.findByQuestionIdIn(List.of(e.oldQuestionId()));
        Question replacement = e.newQuestionId() == null ? null : questions.findById(e.newQuestionId()).orElseThrow();
        Map<UUID, List<ItemResponse>> byAttempt = affected.stream().collect(Collectors.groupingBy(ItemResponse::getAttemptId));
        for (var entry : byAttempt.entrySet()) {
            TestAttempt a = attempts.findById(entry.getKey()).orElseThrow();
            Double before = a.getPercent();
            Double beforePoints = a.getPoints();
            for (ItemResponse r : entry.getValue()) {
                if (e.withdrawn()) {
                    if (a.getStatus() == TestAttempt.Status.SUBMITTED) r.neutralise("Въпросът е оттеглен и не се брои.");
                } else {
                    r.setQuestionId(replacement.getId());
                    if (a.getStatus() == TestAttempt.Status.SUBMITTED && !"TEACHER".equals(r.getScoredBy())) {
                        r.score(AnswerChecker.check(replacement.getResponseType(), replacement.key(),
                                replacement.getMaxPoints(), r.payload()), "AUTOMATED");
                    }
                }
            }
            if (a.getStatus() != TestAttempt.Status.SUBMITTED) continue;
            TestDefinition t = tests.findById(a.getTestId()).orElseThrow();
            rescoreTotals(a, t, responses.findByAttemptIdOrderByPositionAsc(a.getId()));
            if (!java.util.Objects.equals(before, a.getPercent())) {
                String msg = (e.withdrawn() ? "Въпрос беше оттеглен поради грешка в съдържанието" : "Ключът на въпрос беше поправен")
                        + ". Резултатът ти е преизчислен: " + fmt(before) + "% → " + fmt(a.getPercent()) + "%.";
                notices.save(new CorrectionNotice(a.getId(), a.getStudentId(), e.questionKey(), msg,
                        beforePoints == null ? 0 : beforePoints, a.getPoints()));
                audit.record(e.actorId(), "ATTEMPT_RECALCULATED", "TestAttempt", a.getId(),
                        e.questionKey() + ": " + before + " -> " + a.getPercent());
            }
        }
    }

    private static String fmt(Double d) {
        return d == null ? "–" : String.valueOf(d).replace('.', ',');
    }

    // ------------------------------------------------------------------ views

    private AttemptView view(TestAttempt a) {
        TestDefinition t = tests.findById(a.getTestId()).orElseThrow();
        List<ItemResponse> items = responses.findByAttemptIdOrderByPositionAsc(a.getId());
        Map<UUID, Question> qs = questionsById(items.stream().map(ItemResponse::getQuestionId).toList());
        List<AttemptItem> out = items.stream().map(r -> {
            Question q = qs.get(r.getQuestionId());
            List<String> revealed = q.hints().subList(0, Math.min(r.getHintsUsed(), q.hints().size()));
            return new AttemptItem(r.getPosition(), StudentQuestionView.of(q), r.payload(), r.isMarkedForReview(),
                    r.hasAnswer(), r.getHintsUsed(), revealed, r.getLastRequestId());
        }).toList();
        return new AttemptView(a.getId(), t.getId(), t.getTitle(), kindLabel(t.getKind()), a.getStatus(), a.getStartedAt(),
                a.getDeadlineAt(), Instant.now(), a.getLastPosition(), hintPolicy(a), out);
    }

    private ResultView result(TestAttempt a, boolean forStudent) {
        TestDefinition t = tests.findById(a.getTestId()).orElseThrow();
        List<ItemResponse> items = responses.findByAttemptIdOrderByPositionAsc(a.getId());
        Map<UUID, Question> qs = questionsById(items.stream().map(ItemResponse::getQuestionId).toList());
        boolean keyVisible = !forStudent || reviewMomentReached(a, t);

        Map<String, double[]> skill = new LinkedHashMap<>();
        Map<Misconception, Integer> misc = new LinkedHashMap<>();
        List<ItemReview> reviews = new ArrayList<>();
        for (ItemResponse r : items) {
            Question q = qs.get(r.getQuestionId());
            boolean withdrawn = "WITHDRAWN".equals(r.getScoredBy());
            if (!withdrawn) {
                double[] acc = skill.computeIfAbsent(q.getSkill(), k -> new double[2]);
                acc[0] += r.getPoints() == null ? 0 : r.getPoints();
                acc[1] += q.getMaxPoints();
                if (r.getMisconception() != null && r.getStatus() != AnswerChecker.Status.CORRECT) {
                    misc.merge(r.getMisconception(), 1, Integer::sum);
                }
            }
            QuestionModel.AnswerKey key = q.key();
            reviews.add(new ItemReview(r.getPosition(), StudentQuestionView.of(q), r.payload(), r.getStatus(),
                    r.getPoints(), q.getMaxPoints(), keyVisible ? r.getFeedback() : null,
                    r.getMisconception() == null ? null : r.getMisconception().labelBg,
                    keyVisible ? correctAnswerText(q) : null,
                    keyVisible ? key.solution() : null,
                    CurriculumCatalog.lessonForSkill(q.getSkill()).map(CurriculumCatalog.LessonStop::key).orElse(null),
                    "TEACHER".equals(r.getScoredBy()) ? "Оценено от учител" : withdrawn ? "Оттеглен въпрос" : "Автоматична проверка",
                    r.getReviewComment()));
        }
        List<SkillResult> skills = skill.entrySet().stream().map(e -> new SkillResult(e.getKey(),
                CurriculumCatalog.skillTitle(e.getKey()), AnswerChecker.round(e.getValue()[0]), e.getValue()[1],
                e.getValue()[1] == 0 ? 0 : Math.round(e.getValue()[0] / e.getValue()[1] * 100),
                CurriculumCatalog.lessonForSkill(e.getKey()).map(CurriculumCatalog.LessonStop::key).orElse(null))).toList();
        List<MisconceptionGroup> groups = misc.entrySet().stream()
                .sorted(Map.Entry.<Misconception, Integer>comparingByValue().reversed())
                .map(e -> new MisconceptionGroup(e.getKey(), e.getKey().labelBg, e.getValue(), MistakeLinks.lessonFor(e.getKey())))
                .toList();
        String gradeLabel = a.getGrade() == null ? null
                : t.grading().bands().stream().filter(b -> b.grade() == a.getGrade()).map(GradingScale.Band::labelBg).findFirst().orElse(null);
        List<String> corrections = notices.findByAttemptIdOrderByCreatedAtAsc(a.getId()).stream().map(CorrectionNotice::getMessage).toList();
        return new ResultView(a.getId(), t.getTitle(), kindLabel(t.getKind()), a.getPoints(), a.getMaxPoints(), a.getPercent(),
                a.getGrade(), gradeLabel, t.showsGrade() ? NOT_OFFICIAL : "Резултат в точки от 100. " + NOT_OFFICIAL,
                a.getScoringSource(), skills, groups, keyVisible, reviews, corrections, a.getSubmittedAt());
    }

    private boolean reviewMomentReached(TestAttempt a, TestDefinition t) {
        if (a.getStatus() != TestAttempt.Status.SUBMITTED) return false;
        if (t.getReviewMoment() == TestDefinition.ReviewMoment.AFTER_SUBMIT) return true;
        if (a.getAssignmentId() == null) return true;
        return assignments.rulesFor(a.getAssignmentId(), a.getStudentId())
                .map(r -> r.deadline() == null || Instant.now().isAfter(r.deadline())).orElse(true);
    }

    static String correctAnswerText(Question q) {
        QuestionModel.AnswerKey k = q.key();
        return switch (q.getResponseType()) {
            case SINGLE_CHOICE -> q.prompt().options().stream().filter(o -> o.id().equals(k.correctOptionId()))
                    .map(o -> o.id() + ") " + o.text()).findFirst().orElse(k.correctOptionId());
            case STRUCTURED -> k.parts().stream().map(p -> p.id() + ": " + p.answer()).collect(Collectors.joining("; "));
            case FREE_TEXT -> k.rubric();
            default -> k.answer().replace('.', ',');
        };
    }

    private TestDefinition.HintPolicy hintPolicy(TestAttempt a) {
        if (a.getAssignmentId() != null) {
            Optional<AssignmentPolicy.Rules> r = assignments.rulesFor(a.getAssignmentId(), a.getStudentId());
            if (r.isPresent()) return r.get().hintPolicy();
        }
        return tests.findById(a.getTestId()).map(TestDefinition::getHintPolicy).orElse(TestDefinition.HintPolicy.NONE);
    }

    private TestAttempt owned(UUID studentId, UUID attemptId) {
        TestAttempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Опит"));
        if (!a.getStudentId().equals(studentId)) throw ApiException.forbidden("Опитът не е твой.");
        return a;
    }

    private Map<UUID, Question> questionsById(List<UUID> ids) {
        return questions.findAllById(ids).stream().collect(Collectors.toMap(Question::getId, Function.identity()));
    }
}
