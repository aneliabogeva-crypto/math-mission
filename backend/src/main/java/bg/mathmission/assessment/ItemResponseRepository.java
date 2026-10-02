package bg.mathmission.assessment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemResponseRepository extends JpaRepository<ItemResponse, UUID> {
    List<ItemResponse> findByAttemptIdOrderByPositionAsc(UUID attemptId);
    Optional<ItemResponse> findByAttemptIdAndPosition(UUID attemptId, int position);
    List<ItemResponse> findByQuestionIdIn(Collection<UUID> questionIds);
    List<ItemResponse> findByAttemptIdIn(Collection<UUID> attemptIds);
    List<ItemResponse> findByStatusAndAttemptIdIn(AnswerChecker.Status status, Collection<UUID> attemptIds);
}
