package bg.mathmission.progress;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeResponseRepository extends JpaRepository<PracticeResponse, UUID> {
    Optional<PracticeResponse> findByRequestId(String requestId);
    List<PracticeResponse> findByStudentIdOrderByCreatedAtAsc(UUID studentId);
    List<PracticeResponse> findByStudentIdAndQuestionIdIn(UUID studentId, Collection<UUID> questionIds);
    List<PracticeResponse> findByStudentIdAndCreatedAtAfter(UUID studentId, Instant after);
}
