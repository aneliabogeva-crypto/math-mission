package bg.mathmission.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_attempt")
public class TestAttempt {

    public enum Status { IN_PROGRESS, SUBMITTED }

    public enum ScoringSource { AUTOMATED, PENDING_TEACHER_REVIEW, TEACHER_REVIEWED }

    @Id
    private UUID id;
    @Column(name = "test_id")
    private UUID testId;
    @Column(name = "student_id")
    private UUID studentId;
    @Column(name = "assignment_id")
    private UUID assignmentId;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "deadline_at")
    private Instant deadlineAt;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "last_position")
    private int lastPosition;
    private Double points;
    @Column(name = "max_points")
    private Double maxPoints;
    private Double percent;
    private Integer grade;
    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_source")
    private ScoringSource scoringSource;
    @Version
    private long version;

    protected TestAttempt() {}

    /** The id may come from the client so that a retried "start" never creates a second attempt. */
    public TestAttempt(UUID id, UUID testId, UUID studentId, UUID assignmentId, Instant deadlineAt) {
        this.id = id;
        this.testId = testId;
        this.studentId = studentId;
        this.assignmentId = assignmentId;
        this.status = Status.IN_PROGRESS;
        this.startedAt = Instant.now();
        this.deadlineAt = deadlineAt;
    }

    public void applyScore(double points, double maxPoints, Integer grade, ScoringSource source) {
        this.points = points;
        this.maxPoints = maxPoints;
        this.percent = maxPoints == 0 ? 0 : Math.round(points / maxPoints * 1000.0) / 10.0;
        this.grade = grade;
        this.scoringSource = source;
    }

    public void submit() {
        this.status = Status.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTestId() { return testId; }
    public UUID getStudentId() { return studentId; }
    public UUID getAssignmentId() { return assignmentId; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public int getLastPosition() { return lastPosition; }
    public void setLastPosition(int p) { this.lastPosition = p; }
    public Double getPoints() { return points; }
    public Double getMaxPoints() { return maxPoints; }
    public Double getPercent() { return percent; }
    public Integer getGrade() { return grade; }
    public ScoringSource getScoringSource() { return scoringSource; }
}
