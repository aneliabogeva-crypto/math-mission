package bg.mathmission.audit;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Joins the caller's transaction (if any) so the audit record commits atomically with the change. */
    @Transactional
    public void record(UUID actorId, String action, String entityType, Object entityId, String details) {
        repository.save(new AuditLog(actorId, action, entityType, String.valueOf(entityId), details));
    }
}
