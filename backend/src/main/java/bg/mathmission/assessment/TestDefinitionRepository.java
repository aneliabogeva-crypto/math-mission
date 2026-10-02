package bg.mathmission.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestDefinitionRepository extends JpaRepository<TestDefinition, UUID> {
    Optional<TestDefinition> findByTestKey(String key);
    List<TestDefinition> findByStatus(TestDefinition.Status status);
    List<TestDefinition> findByCreatedBy(UUID teacherId);
}
