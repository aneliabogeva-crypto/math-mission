package bg.mathmission.classroom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Removing a student ends the membership; the student account itself is untouched. */
@Entity
@Table(name = "class_membership")
public class ClassMembership {

    public enum Status { ACTIVE, REMOVED }

    @Id
    private UUID id;
    @Column(name = "class_id")
    private UUID classId;
    @Column(name = "student_id")
    private UUID studentId;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;
    @Column(name = "joined_at")
    private Instant joinedAt;
    @Column(name = "removed_at")
    private Instant removedAt;

    protected ClassMembership() {}

    public ClassMembership(UUID classId, UUID studentId) {
        this.id = UUID.randomUUID();
        this.classId = classId;
        this.studentId = studentId;
        this.status = Status.ACTIVE;
        this.joinedAt = Instant.now();
    }

    public void remove() {
        this.status = Status.REMOVED;
        this.removedAt = Instant.now();
    }

    public void rejoin() {
        this.status = Status.ACTIVE;
        this.joinedAt = Instant.now();
        this.removedAt = null;
    }

    public UUID getId() { return id; }
    public UUID getClassId() { return classId; }
    public UUID getStudentId() { return studentId; }
    public Status getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }
}
