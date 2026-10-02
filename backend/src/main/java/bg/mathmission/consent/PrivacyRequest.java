package bg.mathmission.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Documented workflow for data export, deletion and post-withdrawal retention. */
@Entity
@Table(name = "privacy_request")
public class PrivacyRequest {

    public enum Kind { EXPORT, DELETION, RETENTION_REVIEW }

    public enum Status { OPEN, COMPLETED }

    @Id
    private UUID id;
    @Column(name = "student_id")
    private UUID studentId;
    @Column(name = "requested_by")
    private UUID requestedBy;
    @Enumerated(EnumType.STRING)
    private Kind kind;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected PrivacyRequest() {}

    public PrivacyRequest(UUID studentId, UUID requestedBy, Kind kind) {
        this.id = UUID.randomUUID();
        this.studentId = studentId;
        this.requestedBy = requestedBy;
        this.kind = kind;
        this.status = Status.OPEN;
        this.createdAt = Instant.now();
    }

    public void complete() {
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public UUID getRequestedBy() { return requestedBy; }
    public Kind getKind() { return kind; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
