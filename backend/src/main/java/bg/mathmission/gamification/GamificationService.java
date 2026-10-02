package bg.mathmission.gamification;

import bg.mathmission.assessment.LearningEvents;
import bg.mathmission.curriculum.CurriculumCatalog;
import bg.mathmission.identity.UserAccountRepository;
import bg.mathmission.progress.ProgressService;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responsible gamification (spec 8.2): experience points, levels, skill badges, rewards for
 * corrected mistakes. No streak penalties, no chance-based rewards, no public ranking.
 */
@Service
public class GamificationService {

    public static final String BADGE_PREFIX = "BADGE_";

    private final RewardRepository rewards;
    private final UserAccountRepository users;

    public GamificationService(RewardRepository rewards, UserAccountRepository users) {
        this.rewards = rewards;
        this.users = users;
    }

    @EventListener
    @Transactional
    public void onAnswer(LearningEvents.AnswerScored e) {
        if (e.source() == LearningEvents.Source.PRACTICE && e.correct()) {
            grant(e.studentId(), "PRACTICE_CORRECT", "Вярна задача", e.hintsUsed() == 0 ? 10 : 5, e.sourceRef());
        }
        if (e.correctsPrevious()) {
            grant(e.studentId(), "CORRECTED_MISTAKE", "Поправена грешка", 20, e.questionKey());
            long fixed = rewards.countByStudentIdAndCode(e.studentId(), "CORRECTED_MISTAKE");
            if (fixed >= 5) grant(e.studentId(), BADGE_PREFIX + "FIXER", "Значка: Ловец на грешки", 50, "5");
        }
    }

    @EventListener
    @Transactional
    public void onSkillSecure(ProgressService.SkillSecured e) {
        grant(e.studentId(), BADGE_PREFIX + "SKILL", "Значка: " + CurriculumCatalog.skillTitle(e.skill()), 40, e.skill());
    }

    @EventListener
    @Transactional
    public void onLesson(LearningEvents.LessonCompleted e) {
        grant(e.studentId(), "LESSON_COMPLETED", "Завършен урок", 50, e.lessonKey());
        grant(e.studentId(), BADGE_PREFIX + "FIRST_LESSON", "Значка: Първа мисия", 20, "1");
    }

    @EventListener
    @Transactional
    public void onTest(LearningEvents.TestSubmitted e) {
        // Rewarded for finishing and reflecting, not for the score.
        grant(e.studentId(), "TEST_COMPLETED", "Завършен тест: " + e.testTitle(), 60, e.attemptId().toString());
    }

    /** Idempotent: the same achievement from the same source is granted at most once. */
    void grant(UUID studentId, String code, String title, int xp, String sourceRef) {
        if (rewards.existsByStudentIdAndCodeAndSourceRef(studentId, code, sourceRef)) return;
        rewards.save(new Reward(studentId, code, title, xp, sourceRef));
        users.findById(studentId).ifPresent(u -> u.addXp(xp));
    }
}
