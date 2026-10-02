package bg.mathmission.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Dynamic estimate of how securely a learner applies one skill. It is a learning signal, never a
 * judgement of intelligence or potential.
 */
@Entity
@Table(name = "skill_mastery")
public class SkillMastery {

    public enum State { NOT_STARTED, NEEDS_PRACTICE, PRACTISING, SECURE, MASTERED }

    /** Spaced repetition after roughly 1, 3, 7 and 14 days. */
    static final int[] REVIEW_DAYS = {1, 3, 7, 14};

    @Id
    private UUID id;
    @Column(name = "student_id")
    private UUID studentId;
    private String skill;
    private double score;
    private int attempts;
    private int correct;
    @Column(name = "hints_used")
    private int hintsUsed;
    @Column(name = "consecutive_errors")
    private int consecutiveErrors;
    @Enumerated(EnumType.STRING)
    private State state;
    @Column(name = "review_stage")
    private int reviewStage;
    @Column(name = "next_review_at")
    private Instant nextReviewAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SkillMastery() {}

    public SkillMastery(UUID studentId, String skill) {
        this.id = UUID.randomUUID();
        this.studentId = studentId;
        this.skill = skill;
        this.state = State.NOT_STARTED;
        this.updatedAt = Instant.now();
    }

    /**
     * Exponentially weighted update. A hint lowers the credit for a correct answer (each level 25%),
     * so mastery reflects independent success.
     */
    public void record(boolean wasCorrect, int hints, Instant now) {
        double credit = wasCorrect ? Math.max(0.25, 1.0 - 0.25 * hints) : 0.0;
        score = attempts == 0 ? credit * 0.6 : 0.7 * score + 0.3 * credit;
        attempts++;
        hintsUsed += hints;
        if (wasCorrect) {
            correct++;
            consecutiveErrors = 0;
        } else {
            consecutiveErrors++;
        }
        boolean dueReview = nextReviewAt != null && !now.isBefore(nextReviewAt);
        if (score >= 0.8 && attempts >= 4) {
            if (state == State.SECURE && dueReview && wasCorrect && hints == 0) {
                reviewStage++;
                if (reviewStage >= REVIEW_DAYS.length) state = State.MASTERED;
            } else if (state != State.MASTERED && state != State.SECURE) {
                state = State.SECURE;
                reviewStage = 0;
            }
            int idx = Math.min(reviewStage, REVIEW_DAYS.length - 1);
            if (dueReview || nextReviewAt == null) nextReviewAt = now.plus(Duration.ofDays(REVIEW_DAYS[idx]));
        } else if (score >= 0.4) {
            state = State.PRACTISING;
        } else {
            state = State.NEEDS_PRACTICE;
        }
        if (!wasCorrect && (state == State.MASTERED || state == State.SECURE) && score < 0.8) {
            state = State.PRACTISING; // a later slip lowers the estimate, it never removes earned badges
            reviewStage = 0;
            nextReviewAt = now.plus(Duration.ofDays(1));
        }
        updatedAt = now;
    }

    public UUID getStudentId() { return studentId; }
    public String getSkill() { return skill; }
    public double getScore() { return score; }
    public int getAttempts() { return attempts; }
    public int getCorrect() { return correct; }
    public int getHintsUsed() { return hintsUsed; }
    public int getConsecutiveErrors() { return consecutiveErrors; }
    public State getState() { return state; }
    public int getReviewStage() { return reviewStage; }
    public Instant getNextReviewAt() { return nextReviewAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
