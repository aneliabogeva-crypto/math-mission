package bg.mathmission.progress;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillMasteryRepository extends JpaRepository<SkillMastery, UUID> {
    Optional<SkillMastery> findByStudentIdAndSkill(UUID studentId, String skill);
    List<SkillMastery> findByStudentId(UUID studentId);
    List<SkillMastery> findByStudentIdIn(Collection<UUID> studentIds);
}
