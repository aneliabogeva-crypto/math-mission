package bg.mathmission.gamification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRepository extends JpaRepository<Reward, UUID> {
    boolean existsByStudentIdAndCodeAndSourceRef(UUID studentId, String code, String sourceRef);
    List<Reward> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
    long countByStudentIdAndCode(UUID studentId, String code);
    List<Reward> findByStudentIdAndCreatedAtAfter(UUID studentId, Instant after);
}
