package bg.mathmission.assessment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestBlueprintRepository extends JpaRepository<TestBlueprint, UUID> {
    List<TestBlueprint> findByStatus(TestBlueprint.Status status);
    List<TestBlueprint> findAllByOrderByAcademicYearDescVersionDesc();
}
