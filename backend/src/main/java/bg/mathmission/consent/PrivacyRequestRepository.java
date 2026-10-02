package bg.mathmission.consent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyRequestRepository extends JpaRepository<PrivacyRequest, UUID> {

    List<PrivacyRequest> findByStatusOrderByCreatedAtAsc(PrivacyRequest.Status status);

    List<PrivacyRequest> findByStudentId(UUID studentId);
}
