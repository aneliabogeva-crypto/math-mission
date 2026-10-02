package bg.mathmission.assessment;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, UUID> {
    List<TestAttempt> findByStudentIdOrderByStartedAtDesc(UUID studentId);
    List<TestAttempt> findByStudentIdAndTestIdOrderByStartedAtAsc(UUID studentId, UUID testId);
    List<TestAttempt> findByTestIdAndStudentIdInOrderByStartedAtAsc(UUID testId, Collection<UUID> studentIds);
    List<TestAttempt> findByIdIn(Collection<UUID> ids);
    long countByStudentIdAndAssignmentId(UUID studentId, UUID assignmentId);
}
