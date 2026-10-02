package bg.mathmission.consent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianConsentRepository extends JpaRepository<GuardianConsent, UUID> {

    Optional<GuardianConsent> findByConsentCode(String code);

    List<GuardianConsent> findByGuardianId(UUID guardianId);

    List<GuardianConsent> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
