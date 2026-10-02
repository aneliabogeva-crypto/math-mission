package bg.mathmission.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Deliberately exposes only insert and read operations: the audit trail is append-only. */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

    AuditLog save(AuditLog log);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, String entityId);

    List<AuditLog> findTop200ByOrderByCreatedAtDesc();

    List<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId);
}
