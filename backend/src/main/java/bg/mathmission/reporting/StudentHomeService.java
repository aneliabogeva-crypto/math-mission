package bg.mathmission.reporting;

import bg.mathmission.assessment.AssessmentService;
import bg.mathmission.assessment.AssessmentViews.TestSummary;
import bg.mathmission.assessment.CorrectionNotice;
import bg.mathmission.assessment.CorrectionNoticeRepository;
import bg.mathmission.assessment.TestAttempt;
import bg.mathmission.assessment.TestAttemptRepository;
import bg.mathmission.assessment.TestDefinition;
import bg.mathmission.assessment.TestDefinitionRepository;
import bg.mathmission.classroom.ClassroomService;
import bg.mathmission.common.ApiException;
import bg.mathmission.curriculum.CurriculumCatalog;
import bg.mathmission.gamification.GamificationService;
import bg.mathmission.gamification.Reward;
import bg.mathmission.gamification.RewardRepository;
import bg.mathmission.identity.UserAccount;
import bg.mathmission.identity.UserAccountRepository;
import bg.mathmission.progress.LessonProgress;
import bg.mathmission.progress.PracticeResponseRepository;
import bg.mathmission.progress.ProgressService;
import bg.mathmission.progress.SkillMastery;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Student home (FR-02): resume action, recommended mission, skills, next test, weekly goal. */
@Service
public class StudentHomeService {

    private static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");

    private final UserAccountRepository users;
    private final ProgressService progress;
    private final AssessmentService assessment;
    private final TestAttemptRepository attempts;
    private final TestDefinitionRepository tests;
    private final ClassroomService classroom;
    private final RewardRepository rewards;
    private final PracticeResponseRepository practice;
    private final CorrectionNoticeRepository notices;

    public StudentHomeService(UserAccountRepository users, ProgressService progress, AssessmentService assessment,
                              TestAttemptRepository attempts, TestDefinitionRepository tests, ClassroomService classroom,
                              RewardRepository rewards, PracticeResponseRepository practice, CorrectionNoticeRepository notices) {
        this.users = users;
        this.progress = progress;
        this.assessment = assessment;
        this.attempts = attempts;
        this.tests = tests;
        this.classroom = classroom;
        this.rewards = rewards;
        this.practice = practice;
        this.notices = notices;
    }

    public record Profile(UUID id, String nickname, String avatar, String goal, int xp, int level, int weeklyGoal, String status) {}

    public record ContinueAction(String type, String key, UUID id, String title, String detail, Instant updatedAt) {}

    public record Mission(String type, String key, String title, String reason) {}

    public record SkillChip(String skill, String title, String state, String stateLabel, int percent, String lessonKey) {}

    public record WeeklyGoal(int target, int activeDays, List<Boolean> days, String message) {}

    public record RewardView(String code, String title, int xp, Instant earnedAt, boolean badge) {}

    public record Home(Profile profile, ContinueAction continueAction, Mission recommended, List<SkillChip> secureSkills,
                       List<SkillChip> practiceSkills, TestSummary nextTest, boolean offerDiagnostic, WeeklyGoal weeklyGoal,
                       List<ClassroomService.AssignmentView> assignments, List<RewardView> recentRewards,
                       List<RewardView> badges, List<String> corrections) {}

    @Transactional(readOnly = true)
    public Home home(UUID studentId) {
        UserAccount u = users.findById(studentId).orElseThrow(() -> ApiException.notFound("Профил"));
        Profile profile = new Profile(u.getId(), u.getNickname(), u.getAvatar(),
                u.getLearningGoal() == null ? null : u.getLearningGoal().name(), u.getXp(), u.level(), u.getWeeklyGoal(),
                u.getStatus().name());

        // ---- continue
        List<TestAttempt> myAttempts = attempts.findByStudentIdOrderByStartedAtDesc(studentId);
        List<ContinueAction> candidates = new ArrayList<>();
        myAttempts.stream().filter(a -> a.getStatus() == TestAttempt.Status.IN_PROGRESS).findFirst().ifPresent(a ->
                candidates.add(new ContinueAction("TEST", null, a.getId(),
                        tests.findById(a.getTestId()).map(TestDefinition::getTitle).orElse("Тест"),
                        "Въпрос " + (a.getLastPosition() + 1), a.getStartedAt())));
        List<LessonProgress> lessons = progress.lessonProgress(studentId);
        lessons.stream().filter(p -> p.getStatus() == LessonProgress.Status.IN_PROGRESS).findFirst().ifPresent(p ->
                candidates.add(new ContinueAction("LESSON", p.getLessonKey(), null,
                        CurriculumCatalog.lesson(p.getLessonKey()).map(CurriculumCatalog.LessonStop::title).orElse(p.getLessonKey()),
                        "Част " + (p.getPosition() + 1) + " от " + ProgressService.LESSON_SECTIONS, p.getUpdatedAt())));
        ContinueAction cont = candidates.stream().max(Comparator.comparing(ContinueAction::updatedAt)).orElse(null);

        // ---- skills
        List<SkillMastery> skills = progress.skills(studentId);
        List<SkillChip> secure = skills.stream()
                .filter(s -> s.getState() == SkillMastery.State.SECURE || s.getState() == SkillMastery.State.MASTERED)
                .map(this::chip).toList();
        List<SkillChip> toPractise = skills.stream()
                .filter(s -> s.getState() == SkillMastery.State.NEEDS_PRACTICE || s.getState() == SkillMastery.State.PRACTISING)
                .sorted(Comparator.comparingDouble(SkillMastery::getScore)).map(this::chip).toList();

        // ---- recommended mission
        Mission mission;
        Set<String> completed = new HashSet<>();
        lessons.stream().filter(p -> p.getStatus() == LessonProgress.Status.COMPLETED).forEach(p -> completed.add(p.getLessonKey()));
        var map = progress.map(studentId);
        var nextLesson = map.stream().flatMap(z -> z.stops().stream())
                .filter(s -> s.available() && !"COMPLETED".equals(s.status())).findFirst();
        if (!toPractise.isEmpty() && toPractise.get(0).lessonKey() != null) {
            SkillChip w = toPractise.get(0);
            mission = new Mission("PRACTICE", w.lessonKey(), "Упражни: " + w.title(), "Това умение още се затвърждава — 10 минути ще помогнат.");
        } else if (nextLesson.isPresent()) {
            mission = new Mission("LESSON", nextLesson.get().key(), nextLesson.get().title(), "Следващата спирка на картата.");
        } else {
            mission = new Mission("REVISION", null, "Смесен преговор", "Поддържай наученото свежо.");
        }

        // ---- next test
        List<TestSummary> available = assessment.availableTests(studentId);
        TestSummary nextTest = available.stream().filter(t -> t.completedAttempts() == 0).findFirst()
                .orElse(available.isEmpty() ? null : available.get(0));
        boolean offerDiagnostic = myAttempts.isEmpty()
                && available.stream().anyMatch(t -> t.kind() == TestDefinition.Kind.DIAGNOSTIC);

        // ---- weekly goal: active days this week, never a streak that can be "lost"
        LocalDate monday = LocalDate.now(SOFIA).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart = monday.atStartOfDay(SOFIA).toInstant();
        Set<LocalDate> active = new HashSet<>();
        practice.findByStudentIdAndCreatedAtAfter(studentId, weekStart).forEach(p -> active.add(LocalDate.ofInstant(p.getCreatedAt(), SOFIA)));
        lessons.stream().filter(p -> p.getUpdatedAt().isAfter(weekStart)).forEach(p -> active.add(LocalDate.ofInstant(p.getUpdatedAt(), SOFIA)));
        myAttempts.stream().filter(a -> a.getStartedAt().isAfter(weekStart)).forEach(a -> active.add(LocalDate.ofInstant(a.getStartedAt(), SOFIA)));
        List<Boolean> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(active.contains(monday.plusDays(i)));
        int target = u.getWeeklyGoal();
        String msg = active.size() >= target ? "Седмичната цел е изпълнена. Браво!"
                : "Още " + (target - active.size()) + " " + (target - active.size() == 1 ? "ден" : "дни") + " с учене тази седмица.";
        WeeklyGoal weekly = new WeeklyGoal(target, active.size(), days, msg);

        // ---- rewards
        List<Reward> rs = rewards.findByStudentIdOrderByCreatedAtDesc(studentId);
        List<RewardView> recent = rs.stream().limit(5).map(StudentHomeService::reward).toList();
        List<RewardView> badges = rs.stream().filter(r -> r.getCode().startsWith(GamificationService.BADGE_PREFIX))
                .map(StudentHomeService::reward).toList();

        List<String> corrections = notices.findByAttemptIdIn(myAttempts.stream().map(TestAttempt::getId).toList())
                .stream().map(CorrectionNotice::getMessage).toList();

        return new Home(profile, cont, mission, secure, toPractise, nextTest, offerDiagnostic, weekly,
                classroom.forStudent(studentId), recent, badges, corrections);
    }

    private SkillChip chip(SkillMastery s) {
        String label = switch (s.getState()) {
            case MASTERED -> "Усвоено";
            case SECURE -> "Затвърдено";
            case PRACTISING -> "Упражнява се";
            case NEEDS_PRACTICE -> "Има нужда от упражнение";
            case NOT_STARTED -> "Не е започнато";
        };
        return new SkillChip(s.getSkill(), CurriculumCatalog.skillTitle(s.getSkill()), s.getState().name(), label,
                (int) Math.round(s.getScore() * 100),
                CurriculumCatalog.lessonForSkill(s.getSkill()).map(CurriculumCatalog.LessonStop::key).orElse(null));
    }

    private static RewardView reward(Reward r) {
        return new RewardView(r.getCode(), r.getTitle(), r.getXp(), r.getCreatedAt(), r.getCode().startsWith(GamificationService.BADGE_PREFIX));
    }
}
