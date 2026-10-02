package bg.mathmission.progress;

import bg.mathmission.assessment.LearningEvents;
import bg.mathmission.common.ApiException;
import bg.mathmission.content.ContentStatus;
import bg.mathmission.content.LessonRepository;
import bg.mathmission.curriculum.CurriculumCatalog;
import bg.mathmission.curriculum.CurriculumCatalog.LessonStop;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {

    public record SkillSecured(UUID studentId, String skill) {}

    /** After this many consecutive errors difficulty stops increasing and prerequisite review is proposed. */
    public static final int ERROR_STOP = 3;

    /** The lesson player shows the 12-step pattern in 11 screens (worked examples and their reasons share one). */
    public static final int LESSON_SECTIONS = 11;

    private static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");

    private final SkillMasteryRepository mastery;
    private final LessonProgressRepository lessonProgress;
    private final LessonRepository lessons;
    private final ApplicationEventPublisher events;

    public ProgressService(SkillMasteryRepository mastery, LessonProgressRepository lessonProgress,
                           LessonRepository lessons, ApplicationEventPublisher events) {
        this.mastery = mastery;
        this.lessonProgress = lessonProgress;
        this.lessons = lessons;
        this.events = events;
    }

    @EventListener
    @Transactional
    public void onAnswer(LearningEvents.AnswerScored e) {
        SkillMastery m = mastery.findByStudentIdAndSkill(e.studentId(), e.skill())
                .orElseGet(() -> mastery.save(new SkillMastery(e.studentId(), e.skill())));
        SkillMastery.State before = m.getState();
        m.record(e.correct(), e.hintsUsed(), Instant.now());
        if (before != SkillMastery.State.SECURE && before != SkillMastery.State.MASTERED
                && m.getState() == SkillMastery.State.SECURE) {
            events.publishEvent(new SkillSecured(e.studentId(), e.skill()));
        }
    }

    // ------------------------------------------------------------------ lessons

    public record LessonPosition(String lessonKey, int position, int maxPosition, String status) {}

    @Transactional
    public LessonPosition saveLessonPosition(UUID studentId, String lessonKey, int position, boolean completed) {
        if (lessons.findByLessonKeyAndStatus(lessonKey, ContentStatus.PUBLISHED).isEmpty()) {
            throw ApiException.notFound("Урок");
        }
        if (position < 0 || position > 50) throw ApiException.badRequest("POSITION", "Невалидна позиция.");
        LessonProgress p = lessonProgress.findByStudentIdAndLessonKey(studentId, lessonKey)
                .orElseGet(() -> lessonProgress.save(new LessonProgress(studentId, lessonKey)));
        if (p.moveTo(position, completed)) {
            events.publishEvent(new LearningEvents.LessonCompleted(studentId, lessonKey));
        }
        return new LessonPosition(lessonKey, p.getPosition(), p.getMaxPosition(), p.getStatus().name());
    }

    public List<LessonProgress> lessonProgress(UUID studentId) {
        return lessonProgress.findByStudentIdOrderByUpdatedAtDesc(studentId);
    }

    public List<SkillMastery> skills(UUID studentId) {
        return mastery.findByStudentId(studentId);
    }

    // ------------------------------------------------------------------ learning map

    public record MapStop(String key, int order, String title, String skill, String status, String statusLabel,
                          int progressPercent, boolean available, List<String> prerequisites, String recommendation) {}

    public record MapZone(String path, String title, String colour, List<MapStop> stops) {}

    @Transactional(readOnly = true)
    public List<MapZone> map(UUID studentId) {
        Set<String> published = lessons.findByStatus(ContentStatus.PUBLISHED).stream()
                .map(l -> l.getLessonKey()).collect(Collectors.toSet());
        Map<String, LessonProgress> progress = lessonProgress.findByStudentIdOrderByUpdatedAtDesc(studentId).stream()
                .collect(Collectors.toMap(LessonProgress::getLessonKey, Function.identity()));
        List<MapZone> zones = new ArrayList<>();
        for (CurriculumCatalog.Path path : CurriculumCatalog.Path.values()) {
            List<MapStop> stops = CurriculumCatalog.LESSONS.stream().filter(l -> l.path() == path).map(l -> {
                LessonProgress p = progress.get(l.key());
                boolean available = published.contains(l.key());
                String status;
                String label;
                int pct = 0;
                if (!available) {
                    status = "COMING_SOON";
                    label = "Подготвя се";
                } else if (p == null) {
                    status = "NOT_STARTED";
                    label = "Не е започнат";
                } else if (p.getStatus() == LessonProgress.Status.COMPLETED) {
                    status = "COMPLETED";
                    label = "Завършен";
                    pct = 100;
                } else {
                    status = "IN_PROGRESS";
                    label = "Започнат";
                    pct = Math.min(95, Math.round(p.getMaxPosition() * 100f / LESSON_SECTIONS));
                }
                List<String> missing = l.prerequisites().stream()
                        .filter(pre -> progress.get(pre) == null || progress.get(pre).getStatus() != LessonProgress.Status.COMPLETED)
                        .toList();
                // Soft progression: lessons are never locked, the map recommends prerequisites first.
                String rec = missing.isEmpty() ? null : "Препоръчваме първо: " + missing.stream()
                        .map(k -> k + " " + CurriculumCatalog.lesson(k).map(LessonStop::title).orElse(""))
                        .collect(Collectors.joining(", "));
                return new MapStop(l.key(), l.order(), l.title(), l.skill(), status, label, pct, available, l.prerequisites(), rec);
            }).toList();
            zones.add(new MapZone(path.name(), path.titleBg, path.colour, stops));
        }
        return zones;
    }

    // ------------------------------------------------------------------ seven-day plan (US-STU-10)

    public record PlanTask(String kind, String skill, String title, String lessonKey, int minutes, String reason) {}

    public record PlanDay(LocalDate date, List<PlanTask> tasks) {}

    @Transactional(readOnly = true)
    public List<PlanDay> revisionPlan(UUID studentId) {
        List<SkillMastery> skills = mastery.findByStudentId(studentId);
        List<SkillMastery> weak = skills.stream()
                .filter(s -> s.getState() == SkillMastery.State.NEEDS_PRACTICE || s.getState() == SkillMastery.State.PRACTISING)
                .sorted(Comparator.comparingDouble(SkillMastery::getScore)).toList();
        List<SkillMastery> review = skills.stream()
                .filter(s -> s.getNextReviewAt() != null).sorted(Comparator.comparing(SkillMastery::getNextReviewAt)).toList();
        List<PlanDay> days = new ArrayList<>();
        LocalDate today = LocalDate.now(SOFIA);
        for (int d = 0; d < 7; d++) {
            LocalDate date = today.plusDays(d);
            List<PlanTask> tasks = new ArrayList<>();
            if (!weak.isEmpty()) {
                SkillMastery w = weak.get(d % weak.size());
                LessonStop stop = CurriculumCatalog.lessonForSkill(w.getSkill()).orElse(null);
                if (w.getConsecutiveErrors() >= ERROR_STOP && stop != null && !stop.prerequisites().isEmpty()) {
                    String pre = stop.prerequisites().get(0);
                    tasks.add(new PlanTask("PREREQUISITE", w.getSkill(), "Преговор: " + CurriculumCatalog.lesson(pre).map(LessonStop::title).orElse(pre),
                            pre, 10, "Няколко поредни грешки: първо затвърди основата, без по-трудни задачи."));
                } else {
                    tasks.add(new PlanTask("PRACTICE", w.getSkill(), "Упражнение: " + CurriculumCatalog.skillTitle(w.getSkill()),
                            stop == null ? null : stop.key(), 10, "Умение, което още се затвърждава."));
                }
            }
            final int day = d;
            review.stream().filter(s -> {
                        LocalDate due = LocalDate.ofInstant(s.getNextReviewAt(), SOFIA);
                        return day == 0 ? !due.isAfter(date) : due.equals(date);
                    })
                    .limit(2)
                    .forEach(s -> tasks.add(new PlanTask("SPACED_REVIEW", s.getSkill(), "Кратък преговор: " + CurriculumCatalog.skillTitle(s.getSkill()),
                            CurriculumCatalog.lessonForSkill(s.getSkill()).map(LessonStop::key).orElse(null), 5,
                            "Повторение след няколко дни помага да запомниш трайно.")));
            if (d % 3 == 2) {
                tasks.add(new PlanTask("MIXED", null, "Смесени задачи (5 бр.)", null, 5, "Смесеният преговор държи всички теми свежи."));
            }
            if (tasks.isEmpty()) {
                tasks.add(new PlanTask("EXPLORE", null, "Продължи по картата с нов урок", null, 15, "Няма слаби умения за днес — продължи напред!"));
            }
            days.add(new PlanDay(date, tasks));
        }
        return days;
    }
}
