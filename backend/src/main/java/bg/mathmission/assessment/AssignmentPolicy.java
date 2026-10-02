package bg.mathmission.assessment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Port implemented by the classroom module so that assessment does not depend on it directly. */
public interface AssignmentPolicy {

    record Rules(UUID assignmentId, String targetType, String targetKey, Instant deadline, int allowedAttempts,
                 TestDefinition.HintPolicy hintPolicy) {}

    /** Rules for this student, or empty if the assignment does not exist or does not target the student. */
    Optional<Rules> rulesFor(UUID assignmentId, UUID studentId);
}
