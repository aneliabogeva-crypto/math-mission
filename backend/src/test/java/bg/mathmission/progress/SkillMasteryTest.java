package bg.mathmission.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillMasteryTest {

    @Test
    void hintsReduceCreditAndRepeatedSuccessBecomesSecure() {
        SkillMastery withHints = new SkillMastery(UUID.randomUUID(), "B.like-terms");
        SkillMastery noHints = new SkillMastery(UUID.randomUUID(), "B.like-terms");
        Instant now = Instant.now();
        for (int i = 0; i < 6; i++) {
            withHints.record(true, 2, now);
            noHints.record(true, 0, now);
        }
        assertThat(withHints.getScore()).isLessThan(noHints.getScore());
        assertThat(noHints.getState()).isEqualTo(SkillMastery.State.SECURE);
        assertThat(noHints.getNextReviewAt()).isEqualTo(now.plus(Duration.ofDays(1)));
    }

    @Test
    void spacedReviewsLeadToMastered() {
        SkillMastery m = new SkillMastery(UUID.randomUUID(), "B.powers");
        Instant t = Instant.now();
        for (int i = 0; i < 6; i++) m.record(true, 0, t);
        for (int day : new int[] {1, 3, 7, 14}) {
            t = m.getNextReviewAt();
            m.record(true, 0, t);
        }
        assertThat(m.getState()).isEqualTo(SkillMastery.State.MASTERED);
    }

    @Test
    void consecutiveErrorsAreCounted() {
        SkillMastery m = new SkillMastery(UUID.randomUUID(), "B.division");
        for (int i = 0; i < 3; i++) m.record(false, 0, Instant.now());
        assertThat(m.getConsecutiveErrors()).isEqualTo(3);
        assertThat(m.getState()).isEqualTo(SkillMastery.State.NEEDS_PRACTICE);
    }
}
