package bg.mathmission.assessment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorrectionNoticeRepository extends JpaRepository<CorrectionNotice, UUID> {
    List<CorrectionNotice> findByAttemptIdOrderByCreatedAtAsc(UUID attemptId);
    List<CorrectionNotice> findByAttemptIdIn(Collection<UUID> attemptIds);
}
