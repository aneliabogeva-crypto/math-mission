package bg.mathmission.content;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Optional<Question> findByQuestionKeyAndStatus(String key, ContentStatus status);

    List<Question> findByQuestionKeyInAndStatus(Collection<String> keys, ContentStatus status);

    List<Question> findByStatus(ContentStatus status);

    List<Question> findByStatusAndSkill(ContentStatus status, String skill);

    List<Question> findByQuestionKeyOrderByVersionDesc(String key);

    List<Question> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    @Query("select coalesce(max(q.version), 0) from Question q where q.questionKey = :key")
    int maxVersion(String key);
}
