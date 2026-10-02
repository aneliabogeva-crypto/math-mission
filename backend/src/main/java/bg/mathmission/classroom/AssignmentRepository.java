package bg.mathmission.classroom;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    List<Assignment> findByClassIdOrderByCreatedAtDesc(UUID classId);
    List<Assignment> findByClassIdInOrderByCreatedAtDesc(Collection<UUID> classIds);
}
