package bg.mathmission.classroom;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassGroupRepository extends JpaRepository<ClassGroup, UUID> {
    Optional<ClassGroup> findByCode(String code);
    List<ClassGroup> findByTeacherIdOrderByCreatedAtAsc(UUID teacherId);
}
