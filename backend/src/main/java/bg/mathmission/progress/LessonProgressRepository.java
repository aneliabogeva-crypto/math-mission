package bg.mathmission.progress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {
    Optional<LessonProgress> findByStudentIdAndLessonKey(UUID studentId, String lessonKey);
    List<LessonProgress> findByStudentIdOrderByUpdatedAtDesc(UUID studentId);
}
