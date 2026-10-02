package bg.mathmission.progress;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Exact resume point inside a lesson (section index) plus the furthest section reached. */
@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Id
    private UUID id;
    @Column(name = "student_id")
    private UUID studentId;
    @Column(name = "lesson_key")
    private String lessonKey;
    private int position;
    @Column(name = "max_position")
    private int maxPosition;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected LessonProgress() {}

    public LessonProgress(UUID studentId, String lessonKey) {
        this.id = UUID.randomUUID();
        this.studentId = studentId;
        this.lessonKey = lessonKey;
        this.status = Status.IN_PROGRESS;
        this.updatedAt = Instant.now();
    }

    /** Positions only move the "furthest" marker forward; going back to re-read is allowed. */
    public boolean moveTo(int newPosition, boolean completed) {
        this.position = newPosition;
        this.maxPosition = Math.max(maxPosition, newPosition);
        this.updatedAt = Instant.now();
        if (completed && status != Status.COMPLETED) {
            status = Status.COMPLETED;
            return true;
        }
        return false;
    }

    public UUID getStudentId() { return studentId; }
    public String getLessonKey() { return lessonKey; }
    public int getPosition() { return position; }
    public int getMaxPosition() { return maxPosition; }
    public Status getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
