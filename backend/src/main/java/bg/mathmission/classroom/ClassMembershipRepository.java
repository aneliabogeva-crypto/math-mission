package bg.mathmission.classroom;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassMembershipRepository extends JpaRepository<ClassMembership, UUID> {
    List<ClassMembership> findByClassIdAndStatus(UUID classId, ClassMembership.Status status);
    Optional<ClassMembership> findByClassIdAndStudentId(UUID classId, UUID studentId);
    List<ClassMembership> findByStudentIdAndStatus(UUID studentId, ClassMembership.Status status);
}
