package bg.mathmission.reporting;

import bg.mathmission.assessment.AssessmentService;
import bg.mathmission.assessment.AssessmentViews.ResultView;
import bg.mathmission.assessment.TestAttempt;
import bg.mathmission.assessment.TestAttemptRepository;
import bg.mathmission.assessment.TestAuthoringService;
import bg.mathmission.assessment.TestBlueprint;
import bg.mathmission.assessment.TestBlueprintRepository;
import bg.mathmission.assessment.TestDefinition;
import bg.mathmission.assessment.TestDefinitionRepository;
import bg.mathmission.classroom.ClassroomService;
import bg.mathmission.common.ApiException;
import bg.mathmission.content.ContentStatus;
import bg.mathmission.content.Lesson;
import bg.mathmission.content.LessonRepository;
import bg.mathmission.identity.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasRole('TEACHER')")
@Tag(name = "Teacher")
public class TeacherController {

    private final ClassroomService classroom;
    private final TestAuthoringService authoring;
    private final TeacherAnalyticsService analytics;
    private final AssessmentService assessment;
    private final TestDefinitionRepository tests;
    private final TestAttemptRepository attempts;
    private final TestBlueprintRepository blueprints;
    private final LessonRepository lessons;

    public TeacherController(ClassroomService classroom, TestAuthoringService authoring, TeacherAnalyticsService analytics,
                             AssessmentService assessment, TestDefinitionRepository tests, TestAttemptRepository attempts,
                             TestBlueprintRepository blueprints, LessonRepository lessons) {
        this.classroom = classroom;
        this.authoring = authoring;
        this.analytics = analytics;
        this.assessment = assessment;
        this.tests = tests;
        this.attempts = attempts;
        this.blueprints = blueprints;
        this.lessons = lessons;
    }

    // ------------------------------------------------------------------ classes (US-TCH-01)

    public record NewClass(@NotBlank String name) {}

    @PostMapping("/classes")
    public ClassroomService.ClassView createClass(@RequestBody NewClass req) {
        return classroom.create(CurrentUser.id(), req.name());
    }

    @GetMapping("/classes")
    public List<ClassroomService.ClassView> classes() {
        return classroom.mine(CurrentUser.id());
    }

    @PostMapping("/classes/{id}/code/revoke")
    public ClassroomService.ClassView revoke(@PathVariable UUID id) {
        return classroom.revokeCode(CurrentUser.id(), id);
    }

    @PostMapping("/classes/{id}/code/regenerate")
    public ClassroomService.ClassView regenerate(@PathVariable UUID id) {
        return classroom.newCode(CurrentUser.id(), id);
    }

    @GetMapping("/classes/{id}/members")
    public List<ClassroomService.MemberView> members(@PathVariable UUID id) {
        return classroom.members(CurrentUser.id(), id);
    }

    @DeleteMapping("/classes/{id}/members/{studentId}")
    @Operation(summary = "Remove a student from the class (the student account is kept)")
    public Map<String, String> remove(@PathVariable UUID id, @PathVariable UUID studentId) {
        classroom.removeStudent(CurrentUser.id(), id, studentId);
        return Map.of("status", "REMOVED");
    }

    // ------------------------------------------------------------------ assignments (US-TCH-02)

    @PostMapping("/classes/{id}/assignments")
    public ClassroomService.AssignmentView assign(@PathVariable UUID id, @RequestBody ClassroomService.AssignmentRequest req) {
        return classroom.assign(CurrentUser.id(), id, req);
    }

    @GetMapping("/classes/{id}/assignments")
    public List<ClassroomService.AssignmentView> assignments(@PathVariable UUID id) {
        return classroom.forClass(CurrentUser.id(), id);
    }

    /** Exactly what students will see: the student-safe view, without answer keys. */
    @GetMapping("/preview/test/{testKey}")
    public Map<String, Object> previewTestAsStudent(@PathVariable String testKey) {
        TestDefinition t = tests.findByTestKey(testKey).orElseThrow(() -> ApiException.notFound("Тест"));
        var p = authoring.preview(t.getId());
        return Map.of("summary", assessment.summary(t, List.of()),
                "questions", p.items().stream().map(TestAuthoringService.PreviewItem::question).toList());
    }

    @GetMapping("/preview/lesson/{key}")
    @Transactional(readOnly = true)
    public Map<String, Object> previewLesson(@PathVariable String key) {
        Lesson l = lessons.findByLessonKeyAndStatus(key, ContentStatus.PUBLISHED).orElseThrow(() -> ApiException.notFound("Урок"));
        return Map.of("key", l.getLessonKey(), "title", l.getTitle(), "content", l.content());
    }

    // ------------------------------------------------------------------ tests (US-TCH-04)

    @GetMapping("/tests")
    public List<Map<String, Object>> catalog() {
        UUID me = CurrentUser.id();
        return tests.findAll().stream()
                .filter(t -> t.getStatus() == TestDefinition.Status.PUBLISHED || t.getCreatedBy().equals(me))
                .filter(t -> t.getKind() != TestDefinition.Kind.TEACHER || t.getCreatedBy().equals(me))
                .map(t -> Map.<String, Object>of("id", t.getId(), "key", t.getTestKey(), "title", t.getTitle(),
                        "kind", t.getKind(), "status", t.getStatus(), "questionCount", t.questionIds().size(),
                        "timeLimitMin", t.getTimeLimitMin()))
                .toList();
    }

    @PostMapping("/tests/generate")
    public TestAuthoringService.Preview generate(@RequestBody TestAuthoringService.GenerateRequest req) {
        return authoring.generate(CurrentUser.id(), req);
    }

    /** Full preview with scoring configuration and answer key (teacher only). */
    @GetMapping("/tests/{id}/preview")
    public TestAuthoringService.Preview preview(@PathVariable UUID id) {
        TestDefinition t = tests.findById(id).orElseThrow(() -> ApiException.notFound("Тест"));
        if (t.getKind() == TestDefinition.Kind.TEACHER && !t.getCreatedBy().equals(CurrentUser.id())) {
            throw ApiException.forbidden("Тестът не е ваш.");
        }
        return authoring.preview(id);
    }

    @PostMapping("/tests/{id}/publish")
    public TestAuthoringService.Preview publish(@PathVariable UUID id) {
        return authoring.publish(CurrentUser.id(), id, false);
    }

    @GetMapping("/assessment-models")
    @Operation(summary = "Active (approved) assessment model versions")
    public List<Map<String, Object>> models() {
        return blueprints.findByStatus(TestBlueprint.Status.APPROVED).stream().map(b -> Map.<String, Object>of(
                "id", b.getId(), "name", b.getName(), "academicYear", b.getAcademicYear(), "version", b.getVersion(),
                "composition", b.composition(), "timeLimitMin", b.getTimeLimitMin())).toList();
    }

    // ------------------------------------------------------------------ analytics (US-TCH-03)

    @GetMapping("/classes/{classId}/tests/{testId}/report")
    public TeacherAnalyticsService.TestReport report(@PathVariable UUID classId, @PathVariable UUID testId) {
        return analytics.testReport(CurrentUser.id(), classId, testId);
    }

    @GetMapping("/classes/{classId}/skills")
    public List<TeacherAnalyticsService.ClassSkillOverview> skills(@PathVariable UUID classId) {
        return analytics.skillOverview(CurrentUser.id(), classId);
    }

    @GetMapping("/attempts/{attemptId}")
    public ResultView attempt(@PathVariable UUID attemptId) {
        TestAttempt a = attempts.findById(attemptId).orElseThrow(() -> ApiException.notFound("Опит"));
        if (!classroom.studentsOfTeacher(CurrentUser.id()).contains(a.getStudentId())) {
            throw ApiException.forbidden("Ученикът не е във ваш клас.");
        }
        return assessment.resultForStaff(attemptId);
    }

    // ------------------------------------------------------------------ review queue (US-TCH-05)

    @GetMapping("/review-queue")
    public List<AssessmentService.ReviewQueueItem> queue() {
        return assessment.reviewQueue(classroom.studentsOfTeacher(CurrentUser.id()));
    }

    public record Score(double points, String comment) {}

    @PostMapping("/review-queue/{itemId}")
    public ResultView score(@PathVariable UUID itemId, @RequestBody Score req) {
        return assessment.teacherScore(CurrentUser.id(), classroom.studentsOfTeacher(CurrentUser.id()), itemId, req.points(), req.comment());
    }
}
