package bg.mathmission.content;

import bg.mathmission.audit.AuditService;
import bg.mathmission.common.ApiException;
import bg.mathmission.common.AppProperties;
import bg.mathmission.common.Codes;
import bg.mathmission.content.QuestionModel.AnswerKey;
import bg.mathmission.content.QuestionModel.Option;
import bg.mathmission.content.QuestionModel.ResponseType;
import bg.mathmission.curriculum.CurriculumCatalog;
import bg.mathmission.identity.Role;
import bg.mathmission.math.MathEngine;
import bg.mathmission.math.MathInputException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoring and two-stage approval. Drafts are never visible to students; publication requires a
 * mathematics review by someone other than the author; new versions replace the published one
 * only after review; defective items can be withdrawn or corrected with recalculation.
 */
@Service
public class ContentService {

    /** Published when scores that used a question version must be recalculated. */
    public record QuestionChanged(String questionKey, UUID oldQuestionId, UUID newQuestionId, boolean withdrawn,
                                  UUID actorId, String reason) {}

    private final QuestionRepository questions;
    private final LessonRepository lessons;
    private final ReviewDecisionRepository decisions;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    public ContentService(QuestionRepository questions, LessonRepository lessons, ReviewDecisionRepository decisions,
                          AuditService audit, ApplicationEventPublisher events, AppProperties props) {
        this.questions = questions;
        this.lessons = lessons;
        this.decisions = decisions;
        this.audit = audit;
        this.events = events;
        this.props = props;
    }

    // ------------------------------------------------------------------ questions

    /** Creates version 1 under an explicit, unused key (used by imports and seed content). */
    @Transactional
    public Question createQuestionWithKey(UUID authorId, String newKey, QuestionDraft draft) {
        if (newKey == null || !newKey.matches("[A-Za-z0-9-]{2,60}")) {
            throw ApiException.badRequest("KEY", "Невалиден ключ на въпрос.");
        }
        if (questions.maxVersion(newKey) > 0) throw ApiException.conflict("KEY_EXISTS", "Ключът вече се използва.");
        Question q = questions.save(new Question(newKey, 1, authorId, draft, props.academicYear()));
        audit.record(authorId, "QUESTION_DRAFT_CREATED", "Question", newKey, "v1");
        return q;
    }

    @Transactional
    public Question createQuestion(UUID authorId, String existingKey, QuestionDraft draft) {
        String key = existingKey != null ? existingKey : "Q-" + draft.path() + "-" + Codes.generate(6);
        int version = questions.maxVersion(key) + 1;
        if (existingKey != null && version == 1) {
            throw ApiException.notFound("Въпрос " + existingKey);
        }
        Question q = questions.save(new Question(key, version, authorId, draft, props.academicYear()));
        audit.record(authorId, "QUESTION_DRAFT_CREATED", "Question", key, "v" + version);
        return q;
    }

    @Transactional
    public Question updateQuestionDraft(UUID authorId, UUID id, QuestionDraft draft) {
        Question q = question(id);
        requireAuthorDraft(authorId, q.getAuthorId(), q.getStatus());
        q.apply(draft);
        return q;
    }

    @Transactional
    public Question submitQuestion(UUID authorId, UUID id) {
        Question q = question(id);
        requireAuthorDraft(authorId, q.getAuthorId(), q.getStatus());
        List<String> problems = validateQuestion(q.toDraft());
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("METADATA_INCOMPLETE", String.join(" ", problems));
        }
        q.setStatus(ContentStatus.IN_REVIEW);
        audit.record(authorId, "QUESTION_SUBMITTED", "Question", q.getQuestionKey(), "v" + q.getVersion());
        return q;
    }

    @Transactional
    public Question reviewQuestion(UUID reviewerId, UUID id, boolean approve, String comment) {
        Question q = question(id);
        if (q.getStatus() != ContentStatus.IN_REVIEW) {
            throw ApiException.conflict("NOT_IN_REVIEW", "Въпросът не чака преглед.");
        }
        if (q.getAuthorId().equals(reviewerId)) {
            throw ApiException.forbidden("Авторът не може да одобрява собственото си съдържание.");
        }
        decisions.save(new ReviewDecision("QUESTION", q.getId(), q.getQuestionKey(), q.getVersion(), reviewerId,
                approve ? "APPROVED" : "REJECTED", comment));
        q.markReviewed(reviewerId, comment);
        if (approve) {
            questions.findByQuestionKeyAndStatus(q.getQuestionKey(), ContentStatus.PUBLISHED)
                    .ifPresent(old -> old.setStatus(ContentStatus.SUPERSEDED));
            questions.flush();
            q.setStatus(ContentStatus.PUBLISHED);
        } else {
            if (comment == null || comment.isBlank()) {
                throw ApiException.badRequest("COMMENT_REQUIRED", "При връщане е нужен коментар към автора.");
            }
            q.setStatus(ContentStatus.DRAFT);
        }
        audit.record(reviewerId, approve ? "QUESTION_APPROVED" : "QUESTION_REJECTED", "Question",
                q.getQuestionKey(), "v" + q.getVersion());
        return q;
    }

    /** Immediately removes a defective published question; history is kept, affected scores are recalculated. */
    @Transactional
    public Question withdrawQuestion(UUID actorId, Role actorRole, String key, String reason) {
        requireCorrector(actorRole);
        Question q = questions.findByQuestionKeyAndStatus(key, ContentStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Публикуван въпрос " + key));
        q.setStatus(ContentStatus.WITHDRAWN);
        audit.record(actorId, "QUESTION_WITHDRAWN", "Question", key, "v" + q.getVersion() + ": " + reason);
        events.publishEvent(new QuestionChanged(key, q.getId(), null, true, actorId, reason));
        return q;
    }

    /** Corrects an answer key: creates a new reviewed version and recalculates affected attempts. */
    @Transactional
    public Question correctAnswerKey(UUID actorId, Role actorRole, String key, AnswerKey newKey, String reason) {
        requireCorrector(actorRole);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "Опишете каква е корекцията.");
        }
        Question old = questions.findByQuestionKeyAndStatus(key, ContentStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Публикуван въпрос " + key));
        QuestionDraft d = old.toDraft();
        QuestionDraft corrected = new QuestionDraft(d.path(), d.skill(), d.learningOutcome(), d.responseType(),
                d.difficulty(), d.estimatedSeconds(), d.maxPoints(), d.misconception(), d.prompt(), newKey, d.hints(),
                d.sourceDeclaration());
        List<String> problems = validateQuestion(corrected);
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("METADATA_INCOMPLETE", String.join(" ", problems));
        }
        Question next = new Question(key, old.getVersion() + 1, old.getAuthorId(), corrected, old.getAcademicYear());
        next.markReviewed(actorId, "Корекция на ключа: " + reason);
        old.setStatus(ContentStatus.SUPERSEDED);
        questions.saveAndFlush(old);
        next.setStatus(ContentStatus.PUBLISHED);
        questions.save(next);
        decisions.save(new ReviewDecision("QUESTION", next.getId(), key, next.getVersion(), actorId, "KEY_CORRECTION", reason));
        audit.record(actorId, "ANSWER_KEY_CORRECTED", "Question", key,
                "v" + old.getVersion() + " -> v" + next.getVersion() + ": " + reason);
        events.publishEvent(new QuestionChanged(key, old.getId(), next.getId(), false, actorId, reason));
        return next;
    }

    /** Metadata completeness check from spec 4.3; also proves the key is machine-checkable. */
    public List<String> validateQuestion(QuestionDraft d) {
        List<String> p = new ArrayList<>();
        if (d.path() == null || !Set.of("A", "B", "C", "D").contains(d.path())) p.add("Тема (път A–D) липсва.");
        if (blank(d.skill()) || CurriculumCatalog.lessonForSkill(d.skill()).isEmpty()) p.add("Уменето не е от учебната програма.");
        if (blank(d.learningOutcome())) p.add("Липсва очакван резултат от обучението.");
        if (d.responseType() == null) p.add("Липсва тип на отговора.");
        if (d.difficulty() == null) p.add("Липсва трудност.");
        if (d.estimatedSeconds() <= 0) p.add("Липсва очаквано време.");
        if (d.maxPoints() <= 0) p.add("Липсват максимални точки.");
        if (d.misconception() == null) p.add("Липсва свързана типична грешка.");
        if (blank(d.sourceDeclaration())) p.add("Липсва източник или декларация за оригинално съдържание.");
        if (d.hints() == null || d.hints().size() != 3 || d.hints().stream().anyMatch(ContentService::blank))
            p.add("Нужни са три нива подсказки.");
        if (d.prompt() == null || blank(d.prompt().text())) p.add("Липсва условие.");
        AnswerKey k = d.key();
        if (k == null || blank(k.solution())) {
            p.add("Липсва пълно решение.");
            return p;
        }
        if (k.distractors() == null || k.distractors().isEmpty()) p.add("Липсва обяснение на вероятен грешен отговор.");
        if (d.responseType() == ResponseType.SINGLE_CHOICE && d.prompt() != null) {
            List<Option> opts = d.prompt().options();
            if (opts == null || opts.size() < 3) p.add("Нужни са поне три възможни отговора.");
            else {
                if (opts.stream().noneMatch(o -> o.id().equals(k.correctOptionId()))) p.add("Верният отговор не е сред вариантите.");
                for (Option o : opts) {
                    if (!o.id().equals(k.correctOptionId()) && (k.distractors() == null
                            || k.distractors().stream().noneMatch(x -> o.id().equals(x.match()) && !blank(x.explanation())))) {
                        p.add("Вариант " + o.id() + " няма обяснение защо е грешен.");
                    }
                }
            }
        }
        try {
            switch (d.responseType()) {
                case NUMERIC -> MathEngine.normalise(k.answer()).asPolynomial().constantValue();
                case EXPRESSION, STEPS -> MathEngine.normalise(k.answer());
                case STRUCTURED -> {
                    if (k.parts() == null || k.parts().isEmpty()) p.add("Структурираният въпрос няма части.");
                    else {
                        double sum = k.parts().stream().mapToDouble(QuestionModel.PartKey::points).sum();
                        if (Math.abs(sum - d.maxPoints()) > 1e-6) p.add("Точките по части не са равни на максималните.");
                    }
                    if (blank(k.rubric())) p.add("Липсва критерий за оценяване (рубрика).");
                }
                case FREE_TEXT -> { if (blank(k.rubric())) p.add("Липсва критерий за оценяване (рубрика)."); }
                default -> { }
            }
        } catch (MathInputException e) {
            p.add("Ключът не може да бъде проверен: " + e.getMessage());
        } catch (NullPointerException e) {
            p.add("Липсва верен отговор.");
        }
        return p;
    }

    // ------------------------------------------------------------------ lessons

    @Transactional
    public Lesson createLesson(UUID authorId, String lessonKey, LessonModel content) {
        var stop = CurriculumCatalog.lesson(lessonKey).orElseThrow(() -> ApiException.notFound("Урок " + lessonKey));
        int version = lessons.maxVersion(lessonKey) + 1;
        Lesson l = lessons.save(new Lesson(lessonKey, version, stop.path().name(), stop.title(), stop.learningOutcome(),
                props.academicYear(), content, authorId));
        audit.record(authorId, "LESSON_DRAFT_CREATED", "Lesson", lessonKey, "v" + version);
        return l;
    }

    @Transactional
    public Lesson updateLessonDraft(UUID authorId, UUID id, String title, LessonModel content) {
        Lesson l = lesson(id);
        requireAuthorDraft(authorId, l.getAuthorId(), l.getStatus());
        l.setContent(title == null ? l.getTitle() : title, content);
        return l;
    }

    @Transactional
    public Lesson submitLesson(UUID authorId, UUID id) {
        Lesson l = lesson(id);
        requireAuthorDraft(authorId, l.getAuthorId(), l.getStatus());
        List<String> errors = l.content().validatePattern();
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("LESSON_PATTERN", String.join(" ", errors));
        }
        l.setStatus(ContentStatus.IN_REVIEW);
        audit.record(authorId, "LESSON_SUBMITTED", "Lesson", l.getLessonKey(), "v" + l.getVersion());
        return l;
    }

    @Transactional
    public Lesson reviewLesson(UUID reviewerId, UUID id, boolean approve, String comment) {
        Lesson l = lesson(id);
        if (l.getStatus() != ContentStatus.IN_REVIEW) {
            throw ApiException.conflict("NOT_IN_REVIEW", "Урокът не чака преглед.");
        }
        if (l.getAuthorId().equals(reviewerId)) {
            throw ApiException.forbidden("Авторът не може да одобрява собственото си съдържание.");
        }
        if (approve) {
            List<String> keys = l.content().allQuestionKeys();
            List<Question> published = questions.findByQuestionKeyInAndStatus(keys, ContentStatus.PUBLISHED);
            if (published.size() < Set.copyOf(keys).size()) {
                throw ApiException.conflict("QUESTIONS_NOT_PUBLISHED", "Всички задачи в урока трябва първо да са прегледани и публикувани.");
            }
        } else if (comment == null || comment.isBlank()) {
            throw ApiException.badRequest("COMMENT_REQUIRED", "При връщане е нужен коментар към автора.");
        }
        decisions.save(new ReviewDecision("LESSON", l.getId(), l.getLessonKey(), l.getVersion(), reviewerId,
                approve ? "APPROVED" : "REJECTED", comment));
        l.markReviewed(reviewerId, comment);
        if (approve) {
            lessons.findByLessonKeyAndStatus(l.getLessonKey(), ContentStatus.PUBLISHED)
                    .ifPresent(old -> old.setStatus(ContentStatus.SUPERSEDED));
            lessons.flush();
            l.setStatus(ContentStatus.PUBLISHED);
        } else {
            l.setStatus(ContentStatus.DRAFT);
        }
        audit.record(reviewerId, approve ? "LESSON_APPROVED" : "LESSON_REJECTED", "Lesson", l.getLessonKey(), "v" + l.getVersion());
        return l;
    }

    // ------------------------------------------------------------------ helpers

    public Question question(UUID id) {
        return questions.findById(id).orElseThrow(() -> ApiException.notFound("Въпрос"));
    }

    public Lesson lesson(UUID id) {
        return lessons.findById(id).orElseThrow(() -> ApiException.notFound("Урок"));
    }

    private static void requireAuthorDraft(UUID actor, UUID author, ContentStatus status) {
        if (!actor.equals(author)) throw ApiException.forbidden("Само авторът може да редактира чернова.");
        if (status != ContentStatus.DRAFT) throw ApiException.conflict("NOT_DRAFT", "Само чернови могат да се редактират. Създайте нова версия.");
    }

    private static void requireCorrector(Role role) {
        if (role != Role.REVIEWER && role != Role.ADMIN) {
            throw ApiException.forbidden("Само рецензент или администратор може да оттегля и коригира съдържание.");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
