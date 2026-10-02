package bg.mathmission.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Transparent notice shown to the student and teacher when a result changed after a correction. */
@Entity
@Table(name = "correction_notice")
public class CorrectionNotice {

    @Id
    private UUID id;
    @Column(name = "attempt_id")
    private UUID attemptId;
    @Column(name = "student_id")
    private UUID studentId;
    @Column(name = "question_key")
    private String questionKey;
    private String message;
    @Column(name = "old_points")
    private double oldPoints;
    @Column(name = "new_points")
    private double newPoints;
    @Column(name = "created_at")
    private Instant createdAt;

    protected CorrectionNotice() {}

    public CorrectionNotice(UUID attemptId, UUID studentId, String questionKey, String message, double oldPoints, double newPoints) {
        this.id = UUID.randomUUID();
        this.attemptId = attemptId;
        this.studentId = studentId;
        this.questionKey = questionKey;
        this.message = message;
        this.oldPoints = oldPoints;
        this.newPoints = newPoints;
        this.createdAt = Instant.now();
    }

    public UUID getAttemptId() { return attemptId; }
    public String getQuestionKey() { return questionKey; }
    public String getMessage() { return message; }
    public double getOldPoints() { return oldPoints; }
    public double getNewPoints() { return newPoints; }
    public Instant getCreatedAt() { return createdAt; }
}
