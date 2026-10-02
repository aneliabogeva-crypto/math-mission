package bg.mathmission.reporting;

import bg.mathmission.assessment.TestAttempt;
import bg.mathmission.assessment.TestAttemptRepository;
import bg.mathmission.assessment.TestDefinition;
import bg.mathmission.assessment.TestDefinitionRepository;
import bg.mathmission.consent.ConsentService;
import bg.mathmission.consent.GuardianConsent;
import bg.mathmission.curriculum.CurriculumCatalog;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guardian portal (US-GUA-01/02): a weekly summary, not real-time monitoring; supportive
 * suggestions, never judgements about intelligence or character.
 */
@Service
public class GuardianService {

    private static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");

    private final ConsentService consents;
    private final UserAccountRepository users;
    private final ProgressService progress;
    private final PracticeResponseRepository practice;
    private final TestAttemptRepository attempts;
    private final TestDefinitionRepository tests;
    private final RewardRepository rewards;

    public GuardianService(ConsentService consents, UserAccountRepository users, ProgressService progress,
                           PracticeResponseRepository practice, TestAttemptRepository attempts,
                           TestDefinitionRepository tests, RewardRepository rewards) {
        this.consents = consents;
        this.users = users;
        this.progress = progress;
        this.practice = practice;
        this.attempts = attempts;
        this.tests = tests;
        this.rewards = rewards;
    }

    public record ChildSummary(UUID consentId, UUID studentId, String nickname, String accountStatus,
                               String consentStatus, String consentVersion, Instant consentGrantedAt,
                               String verificationStatus, String notificationFrequency,
                               LocalDate weekStart, int activeDays, int lessonsCompletedThisWeek, int practiceAnswersThisWeek,
                               List<String> testsThisWeek, List<String> secureSkills, List<String> supportAreas,
                               List<String> suggestions) {}

    @Transactional(readOnly = true)
    public List<ChildSummary> summaries(UUID guardianId) {
        List<ChildSummary> out = new ArrayList<>();
        for (GuardianConsent c : consents.forGuardian(guardianId)) {
            out.add(summary(c));
        }
        return out;
    }

    private ChildSummary summary(GuardianConsent c) {
        UserAccount s = users.findById(c.getStudentId()).orElseThrow();
        LocalDate monday = LocalDate.now(SOFIA).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart = monday.atStartOfDay(SOFIA).toInstant();
        Set<LocalDate> days = new HashSet<>();
        var answers = practice.findByStudentIdAndCreatedAtAfter(s.getId(), weekStart);
        answers.forEach(p -> days.add(LocalDate.ofInstant(p.getCreatedAt(), SOFIA)));
        List<LessonProgress> lessons = progress.lessonProgress(s.getId());
        int lessonsDone = (int) lessons.stream()
                .filter(l -> l.getStatus() == LessonProgress.Status.COMPLETED && l.getUpdatedAt().isAfter(weekStart)).count();
        lessons.stream().filter(l -> l.getUpdatedAt().isAfter(weekStart)).forEach(l -> days.add(LocalDate.ofInstant(l.getUpdatedAt(), SOFIA)));
        List<String> testLines = new ArrayList<>();
        for (TestAttempt a : attempts.findByStudentIdOrderByStartedAtDesc(s.getId())) {
            if (a.getStatus() != TestAttempt.Status.SUBMITTED || a.getSubmittedAt().isBefore(weekStart)) continue;
            days.add(LocalDate.ofInstant(a.getSubmittedAt(), SOFIA));
            String title = tests.findById(a.getTestId()).map(TestDefinition::getTitle).orElse("Тест");
            testLines.add(title + ": " + String.valueOf(a.getPercent()).replace('.', ',') + "%");
        }
        List<SkillMastery> skills = progress.skills(s.getId());
        List<String> secure = skills.stream()
                .filter(m -> m.getState() == SkillMastery.State.SECURE || m.getState() == SkillMastery.State.MASTERED)
                .map(m -> CurriculumCatalog.skillTitle(m.getSkill())).toList();
        List<String> support = skills.stream()
                .filter(m -> m.getState() == SkillMastery.State.NEEDS_PRACTICE)
                .map(m -> CurriculumCatalog.skillTitle(m.getSkill())).toList();
        List<String> suggestions = new ArrayList<>();
        if (!support.isEmpty()) {
            suggestions.add("Помолете детето да ви обясни как решава задача от „" + support.get(0)
                    + "“. Обяснението на глас помага да се открие грешката.");
        }
        if (days.size() < 2) {
            suggestions.add("Кратки сесии от 10–15 минути, два-три пъти седмично, работят по-добре от едно дълго учене.");
        }
        suggestions.add("Похвалете усилието и поправените грешки, а не само резултата.");
        return new ChildSummary(c.getId(), s.getId(), s.getNickname(), s.getStatus().name(), c.getStatus().name(),
                c.getTextVersion(), c.getGrantedAt(), c.getVerificationStatus(), c.getNotificationFrequency().name(), monday,
                days.size(), lessonsDone, answers.size(), testLines, secure, support, suggestions);
    }

    /** Machine-readable export of the child's data (documented export workflow). */
    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID guardianId, UUID consentId) {
        GuardianConsent c = consents.ownedBy(guardianId, consentId);
        UserAccount s = users.findById(c.getStudentId()).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now());
        out.put("profile", Map.of("nickname", s.getNickname(), "avatar", String.valueOf(s.getAvatar()),
                "learningGoal", String.valueOf(s.getLearningGoal()), "confidence", String.valueOf(s.getConfidence()),
                "createdAt", s.getCreatedAt(), "xp", s.getXp()));
        out.put("consent", Map.of("version", c.getTextVersion(), "scope", c.getScope(), "status", c.getStatus(),
                "verification", c.getVerificationStatus(), "grantedAt", String.valueOf(c.getGrantedAt())));
        out.put("skills", progress.skills(s.getId()).stream().map(m -> Map.of("skill", m.getSkill(), "state", m.getState(),
                "score", m.getScore(), "attempts", m.getAttempts())).toList());
        out.put("lessons", progress.lessonProgress(s.getId()).stream().map(l -> Map.of("lesson", l.getLessonKey(),
                "status", l.getStatus(), "updatedAt", l.getUpdatedAt())).toList());
        out.put("tests", attempts.findByStudentIdOrderByStartedAtDesc(s.getId()).stream().map(a -> Map.of(
                "attemptId", a.getId(), "status", a.getStatus(), "startedAt", a.getStartedAt(),
                "percent", String.valueOf(a.getPercent()))).toList());
        out.put("practiceAnswers", practice.findByStudentIdOrderByCreatedAtAsc(s.getId()).size());
        out.put("rewards", rewards.findByStudentIdOrderByCreatedAtDesc(s.getId()).stream()
                .map(r -> Map.of("title", r.getTitle(), "xp", r.getXp(), "earnedAt", r.getCreatedAt())).toList());
        return out;
    }
}
