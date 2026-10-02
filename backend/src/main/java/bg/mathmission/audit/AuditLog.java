package bg.mathmission.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Append-only audit record. Never contains student answers. */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;
    @Column(name = "actor_id")
    private UUID actorId;
    private String action;
    @Column(name = "entity_type")
    private String entityType;
    @Column(name = "entity_id")
    private String entityId;
    private String details;
    @Column(name = "created_at")
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(UUID actorId, String action, String entityType, String entityId, String details) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
