package bg.mathmission.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    Optional<Lesson> findByLessonKeyAndStatus(String key, ContentStatus status);

    List<Lesson> findByStatus(ContentStatus status);

    @Query("select coalesce(max(l.version), 0) from Lesson l where l.lessonKey = :key")
    int maxVersion(String key);
}
