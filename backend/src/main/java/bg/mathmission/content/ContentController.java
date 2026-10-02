package bg.mathmission.content;

import bg.mathmission.audit.AuditLog;
import bg.mathmission.audit.AuditLogRepository;
import bg.mathmission.identity.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Content authoring, two-stage approval, withdrawal and correction (US-CNT-01..03). */
@RestController
@RequestMapping("/api/content")
@Tag(name = "Content")
public class ContentController {

    private final ContentService content;
    private final QuestionRepository questions;
    private final LessonRepository lessons;
    private final ReviewDecisionRepository decisions;
    private final AuditLogRepository audit;

    public ContentController(ContentService content, QuestionRepository questions, LessonRepository lessons,
                             ReviewDecisionRepository decisions, AuditLogRepository audit) {
        this.content = content;
        this.questions = questions;
        this.lessons = lessons;
        this.decisions = decisions;
        this.audit = audit;
    }

    /** Full staff view of a question version, including key and metadata. */
    public record QuestionAdminView(UUID id, String key, int version, ContentStatus status, QuestionDraft draft,
                                    UUID authorId, UUID reviewerId, String reviewComment, String academicYear,
                                    List<String> validationProblems) {
        static QuestionAdminView of(Question q, List<String> problems) {
            return new QuestionAdminView(q.getId(), q.getQuestionKey(), q.getVersion(), q.getStatus(), q.toDraft(),
                    q.getAuthorId(), q.getReviewerId(), q.getReviewComment(), q.getAcademicYear(), problems);
        }
    }

    public record LessonAdminView(UUID id, String key, int version, ContentStatus status, String title, LessonModel content,
                                  UUID authorId, UUID reviewerId, String reviewComment, String academicYear,
                                  String learningOutcome, List<String> patternProblems) {
        static LessonAdminView of(Lesson l) {
            LessonModel m = l.content();
            return new LessonAdminView(l.getId(), l.getLessonKey(), l.getVersion(), l.getStatus(), l.getTitle(), m,
                    l.getAuthorId(), l.getReviewerId(), l.getReviewComment(), l.getAcademicYear(), l.getLearningOutcome(),
                    m.validatePattern());
        }
    }

    // ------------------------------------------------------------------ questions

    @GetMapping("/questions")
    @PreAuthorize("hasAnyRole('AUTHOR','REVIEWER','ADMIN')")
    @Transactional(readOnly = true)
    public List<QuestionAdminView> listQuestions(@RequestParam(required = false) ContentStatus status) {
        return (status == null ? questions.findAll() : questions.findByStatus(status)).stream()
                .map(q -> QuestionAdminView.of(q, List.of())).toList();
    }

    @GetMapping("/questions/{id}")
    @PreAuthorize("hasAnyRole('AUTHOR','REVIEWER','ADMIN')")
    @Transactional(readOnly = true)
    public QuestionAdminView question(@PathVariable UUID id) {
        Question q = content.question(id);
        return QuestionAdminView.of(q, content.validateQuestion(q.toDraft()));
    }

    public record NewQuestion(String existingKey, QuestionDraft draft) {}

    @PostMapping("/questions")
    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    public QuestionAdminView create(@RequestBody NewQuestion req) {
        Question q = content.createQuestion(CurrentUser.id(), req.existingKey(), req.draft());
        return QuestionAdminView.of(q, content.validateQuestion(q.toDraft()));
    }

    @PutMapping("/questions/{id}")
    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    public QuestionAdminView update(@PathVariable UUID id, @RequestBody QuestionDraft draft) {
        Question q = content.updateQuestionDraft(CurrentUser.id(), id, draft);
        return QuestionAdminView.of(q, content.validateQuestion(q.toDraft()));
    }

    @PostMapping("/questions/{id}/submit")
    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    public QuestionAdminView submit(@PathVariable UUID id) {
        return QuestionAdminView.of(content.submitQuestion(CurrentUser.id(), id), List.of());
    }

    public record Review(boolean approve, String comment) {}

    @PostMapping("/questions/{id}/review")
    @PreAuthorize("hasRole('REVIEWER')")
    @Transactional
    public QuestionAdminView review(@PathVariable UUID id, @RequestBody Review req) {
        return QuestionAdminView.of(content.reviewQuestion(CurrentUser.id(), id, req.approve(), req.comment()), List.of());
    }

    public record Withdraw(String reason) {}

    @PostMapping("/questions/by-key/{key}/withdraw")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    @Transactional
    public QuestionAdminView withdraw(@PathVariable String key, @RequestBody Withdraw req) {
        return QuestionAdminView.of(content.withdrawQuestion(CurrentUser.id(), CurrentUser.get().role(), key, req.reason()), List.of());
    }

    public record Correction(QuestionModel.AnswerKey key, String reason) {}

    @PostMapping("/questions/by-key/{key}/correct-key")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    @Transactional
    public QuestionAdminView correct(@PathVariable String key, @RequestBody Correction req) {
        return QuestionAdminView.of(content.correctAnswerKey(CurrentUser.id(), CurrentUser.get().role(), key, req.key(), req.reason()), List.of());
    }

    @GetMapping("/history/{key}")
    @PreAuthorize("hasAnyRole('AUTHOR','REVIEWER','ADMIN')")
    public Map<String, Object> history(@PathVariable String key) {
        List<AuditLog> trail = audit.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("Question", key);
        if (trail.isEmpty()) trail = audit.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("Lesson", key);
        return Map.of("reviews", decisions.findByContentKeyOrderByCreatedAtAsc(key), "audit", trail);
    }

    // ------------------------------------------------------------------ lessons

    @GetMapping("/lessons")
    @PreAuthorize("hasAnyRole('AUTHOR','REVIEWER','ADMIN')")
    @Transactional(readOnly = true)
    public List<LessonAdminView> listLessons(@RequestParam(required = false) ContentStatus status) {
        return (status == null ? lessons.findAll() : lessons.findByStatus(status)).stream().map(LessonAdminView::of).toList();
    }

    public record NewLesson(String lessonKey, LessonModel content) {}

    @PostMapping("/lessons")
    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    public LessonAdminView createLesson(@RequestBody NewLesson req) {
        return LessonAdminView.of(content.createLesson(CurrentUser.id(), req.lessonKey(), req.content()));
    }

    public record LessonUpdate(String title, LessonModel content) {}

    @PutMapping("/lessons/{id}")
    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    public LessonAdminView updateLesson(@PathVariable UUID id, @RequestBody LessonUpdate req) {
        return LessonAdminView.of(content.updateLessonDraft(CurrentUser.id(), id, req.title(), req.content()));
    }

    @PostMapping("/lessons/{id}/submit")
    @PreAuthorize("hasRole('AUTHOR')")
    @Transactional
    public LessonAdminView submitLesson(@PathVariable UUID id) {
        return LessonAdminView.of(content.submitLesson(CurrentUser.id(), id));
    }

    @PostMapping("/lessons/{id}/review")
    @PreAuthorize("hasRole('REVIEWER')")
    @Transactional
    public LessonAdminView reviewLesson(@PathVariable UUID id, @RequestBody Review req) {
        return LessonAdminView.of(content.reviewLesson(CurrentUser.id(), id, req.approve(), req.comment()));
    }
}
