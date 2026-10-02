package bg.mathmission.reporting;

import bg.mathmission.assessment.AnswerChecker;
import bg.mathmission.assessment.AssessmentService;
import bg.mathmission.assessment.AssessmentViews.AttemptView;
import bg.mathmission.assessment.AssessmentViews.ResultView;
import bg.mathmission.assessment.AssessmentViews.SaveAck;
import bg.mathmission.assessment.AssessmentViews.TestSummary;
import bg.mathmission.assessment.ItemResponse;
import bg.mathmission.assessment.ItemResponseRepository;
import bg.mathmission.assessment.TestAttempt;
import bg.mathmission.assessment.TestAttemptRepository;
import bg.mathmission.classroom.ClassroomService;
import bg.mathmission.common.ApiException;
import bg.mathmission.content.ContentStatus;
import bg.mathmission.content.Lesson;
import bg.mathmission.content.LessonModel;
import bg.mathmission.content.LessonRepository;
import bg.mathmission.content.QuestionModel.AnswerPayload;
import bg.mathmission.content.QuestionModel.Misconception;
import bg.mathmission.content.StudentQuestionView;
import bg.mathmission.curriculum.ReferenceLibrary;
import bg.mathmission.identity.AuthController;
import bg.mathmission.identity.CurrentUser;
import bg.mathmission.identity.UserAccount;
import bg.mathmission.identity.UserAccountRepository;
import bg.mathmission.progress.LessonProgress;
import bg.mathmission.progress.PracticeService;
import bg.mathmission.progress.ProgressService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Student")
public class StudentController {

    private final StudentHomeService home;
    private final ProgressService progress;
    private final PracticeService practice;
    private final AssessmentService assessment;
    private final ClassroomService classroom;
    private final LessonRepository lessons;
    private final TestAttemptRepository attempts;
    private final ItemResponseRepository responses;
    private final UserAccountRepository users;

    public StudentController(StudentHomeService home, ProgressService progress, PracticeService practice,
                             AssessmentService assessment, ClassroomService classroom, LessonRepository lessons,
                             TestAttemptRepository attempts, ItemResponseRepository responses, UserAccountRepository users) {
        this.home = home;
        this.progress = progress;
        this.practice = practice;
        this.assessment = assessment;
        this.classroom = classroom;
        this.lessons = lessons;
        this.attempts = attempts;
        this.responses = responses;
        this.users = users;
    }

    @GetMapping("/home")
    public StudentHomeService.Home home() {
        return home.home(CurrentUser.currentId());
    }

    public record ProfileUpdate(String avatar, UserAccount.LearningGoal goal, @Min(1) @Max(7) Integer weeklyGoal,
                                @Min(1) @Max(5) Integer confidence) {}

    @PatchMapping("/profile")
    @Transactional
    public StudentHomeService.Profile updateProfile(@Valid @RequestBody ProfileUpdate req) {
        UserAccount u = users.findById(CurrentUser.currentId()).orElseThrow();
        if (req.avatar() != null) {
            if (!AuthController.AVATARS.contains(req.avatar())) throw ApiException.badRequest("AVATAR", "Избери аватар от списъка.");
            u.setAvatar(req.avatar());
        }
        if (req.goal() != null) u.setLearningGoal(req.goal());
        if (req.weeklyGoal() != null) u.setWeeklyGoal(req.weeklyGoal());
        if (req.confidence() != null) u.setConfidence(req.confidence());
        return new StudentHomeService.Profile(u.getId(), u.getNickname(), u.getAvatar(),
                u.getLearningGoal() == null ? null : u.getLearningGoal().name(), u.getXp(), u.level(), u.getWeeklyGoal(),
                u.getStatus().name());
    }

    @GetMapping("/map")
    public List<ProgressService.MapZone> map() {
        return progress.map(CurrentUser.currentId());
    }

    @GetMapping("/plan")
    public List<ProgressService.PlanDay> plan() {
        return progress.revisionPlan(CurrentUser.currentId());
    }

    // ------------------------------------------------------------------ lessons

    public record LessonView(String key, String path, String title, int version, String academicYear,
                             String learningOutcome, LessonModel content, Map<String, StudentQuestionView> questions,
                             int position, int maxPosition, String status) {}

    /** Only published lessons are visible; drafts never reach the student client. */
    @GetMapping("/lessons/{key}")
    @Transactional(readOnly = true)
    public LessonView lesson(@PathVariable String key) {
        Lesson l = lessons.findByLessonKeyAndStatus(key, ContentStatus.PUBLISHED).orElseThrow(() -> ApiException.notFound("Урок"));
        LessonModel content = l.content();
        Map<String, StudentQuestionView> qs = practice.questions(content.allQuestionKeys()).stream()
                .collect(Collectors.toMap(StudentQuestionView::key, Function.identity(), (a, b) -> a));
        LessonProgress p = progress.lessonProgress(CurrentUser.currentId()).stream()
                .filter(x -> x.getLessonKey().equals(key)).findFirst().orElse(null);
        return new LessonView(l.getLessonKey(), l.getPath(), l.getTitle(), l.getVersion(), l.getAcademicYear(),
                l.getLearningOutcome(), content, qs, p == null ? 0 : p.getPosition(), p == null ? 0 : p.getMaxPosition(),
                p == null ? "NOT_STARTED" : p.getStatus().name());
    }

    public record PositionUpdate(@Min(0) @Max(50) int position, boolean completed) {}

    @PutMapping("/lessons/{key}/position")
    public ProgressService.LessonPosition savePosition(@PathVariable String key, @Valid @RequestBody PositionUpdate req) {
        return progress.saveLessonPosition(CurrentUser.currentId(), key, req.position(), req.completed());
    }

    // ------------------------------------------------------------------ practice

    @GetMapping("/practice/{key}")
    public StudentQuestionView practiceQuestion(@PathVariable String key) {
        return practice.question(key);
    }

    @GetMapping("/practice/{key}/hints/{level}")
    public Map<String, Object> practiceHint(@PathVariable String key, @PathVariable int level) {
        return Map.of("level", level, "text", practice.hint(key, level));
    }

    public record PracticeCheck(@NotBlank @Size(max = 64) String requestId, AnswerPayload answer,
                                @Min(0) @Max(3) int hintsUsed, @Size(max = 20) String lessonKey) {}

    @PostMapping("/practice/{key}/check")
    public PracticeService.Feedback check(@PathVariable String key, @Valid @RequestBody PracticeCheck req) {
        return practice.check(CurrentUser.currentId(), key, req.answer(), req.hintsUsed(), req.requestId(), req.lessonKey());
    }

    @GetMapping("/mistakes")
    @Transactional(readOnly = true)
    public List<PracticeService.MistakeGroup> mistakes() {
        UUID me = CurrentUser.currentId();
        List<UUID> submitted = attempts.findByStudentIdOrderByStartedAtDesc(me).stream()
                .filter(a -> a.getStatus() == TestAttempt.Status.SUBMITTED).map(TestAttempt::getId).toList();
        List<Misconception> fromTests = submitted.isEmpty() ? List.of() : responses.findByAttemptIdIn(submitted).stream()
                .filter(r -> r.getMisconception() != null && r.getStatus() != AnswerChecker.Status.CORRECT)
                .map(ItemResponse::getMisconception).toList();
        return practice.mistakes(me, fromTests);
    }

    @GetMapping("/reference")
    public List<ReferenceLibrary.Entry> reference(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String path) {
        return ReferenceLibrary.search(q, path);
    }

    // ------------------------------------------------------------------ tests

    @GetMapping("/tests")
    public List<TestSummary> tests() {
        return assessment.availableTests(CurrentUser.currentId());
    }

    public record StartAttempt(UUID attemptId, UUID assignmentId) {}

    @PostMapping("/tests/{testId}/attempts")
    public AttemptView start(@PathVariable UUID testId, @RequestBody(required = false) StartAttempt req) {
        return assessment.start(CurrentUser.currentId(), testId, req == null ? null : req.attemptId(), req == null ? null : req.assignmentId());
    }

    @GetMapping("/attempts")
    public List<Map<String, Object>> myAttempts() {
        return attempts.findByStudentIdOrderByStartedAtDesc(CurrentUser.currentId()).stream().map(a -> Map.<String, Object>of(
                "id", a.getId(), "testId", a.getTestId(), "status", a.getStatus(), "startedAt", a.getStartedAt(),
                "percent", a.getPercent() == null ? "" : a.getPercent())).toList();
    }

    @GetMapping("/attempts/{id}")
    public AttemptView attempt(@PathVariable UUID id) {
        return assessment.get(CurrentUser.currentId(), id);
    }

    public record SaveItem(@NotBlank @Size(max = 64) String requestId, AnswerPayload answer, boolean markedForReview,
                           Integer lastPosition) {}

    /** Autosave after every answer. Retried requests with the same requestId are acknowledged without changes. */
    @PutMapping("/attempts/{id}/items/{position}")
    public SaveAck save(@PathVariable UUID id, @PathVariable int position, @Valid @RequestBody SaveItem req) {
        return assessment.save(CurrentUser.currentId(), id, position, req.requestId(), req.answer(), req.markedForReview(), req.lastPosition());
    }

    @PostMapping("/attempts/{id}/items/{position}/hints/{level}")
    public Map<String, Object> testHint(@PathVariable UUID id, @PathVariable int position, @PathVariable int level) {
        return Map.of("level", level, "text", assessment.hint(CurrentUser.currentId(), id, position, level));
    }

    @PostMapping("/attempts/{id}/submit")
    public ResultView submit(@PathVariable UUID id) {
        return assessment.submit(CurrentUser.currentId(), id);
    }

    @GetMapping("/attempts/{id}/result")
    public ResultView result(@PathVariable UUID id) {
        return assessment.result(CurrentUser.currentId(), id);
    }

    // ------------------------------------------------------------------ classes

    public record JoinClass(@NotBlank @Size(max = 12) String code) {}

    @PostMapping("/classes/join")
    public Map<String, Object> join(@Valid @RequestBody JoinClass req) {
        ClassroomService.ClassView c = classroom.join(CurrentUser.currentId(), req.code());
        return Map.of("classId", c.id(), "name", c.name()); // no code or member list is exposed to students
    }
}
