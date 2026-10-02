package bg.mathmission.classroom;

import bg.mathmission.assessment.TestDefinition;
import bg.mathmission.common.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assignment")
public class Assignment {

    public enum TargetType { LESSON, TEST }

    @Id
    private UUID id;
    @Column(name = "class_id")
    private UUID classId;
    @Column(name = "teacher_id")
    private UUID teacherId;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private TargetType targetType;
    @Column(name = "target_key")
    private String targetKey;
    private String title;
    /** Null means the whole class (group assignment); otherwise individual students. */
    @Column(name = "student_ids_json")
    private String studentIdsJson;
    private Instant deadline;
    @Column(name = "allowed_attempts")
    private int allowedAttempts;
    @Enumerated(EnumType.STRING)
    @Column(name = "hint_policy")
    private TestDefinition.HintPolicy hintPolicy;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Assignment() {}

    public Assignment(UUID classId, UUID teacherId, TargetType targetType, String targetKey, String title,
                      List<UUID> studentIds, Instant deadline, int allowedAttempts, TestDefinition.HintPolicy hintPolicy) {
        this.id = UUID.randomUUID();
        this.classId = classId;
        this.teacherId = teacherId;
        this.targetType = targetType;
        this.targetKey = targetKey;
        this.title = title;
        this.studentIdsJson = studentIds == null || studentIds.isEmpty() ? null : Json.write(studentIds);
        this.deadline = deadline;
        this.allowedAttempts = allowedAttempts;
        this.hintPolicy = hintPolicy;
        this.createdAt = Instant.now();
    }

    public List<UUID> studentIds() {
        return studentIdsJson == null ? null : Json.read(studentIdsJson, new TypeReference<List<UUID>>() {});
    }

    public boolean targets(UUID studentId) {
        List<UUID> ids = studentIds();
        return ids == null || ids.contains(studentId);
    }

    public UUID getId() { return id; }
    public UUID getClassId() { return classId; }
    public UUID getTeacherId() { return teacherId; }
    public TargetType getTargetType() { return targetType; }
    public String getTargetKey() { return targetKey; }
    public String getTitle() { return title; }
    public Instant getDeadline() { return deadline; }
    public int getAllowedAttempts() { return allowedAttempts; }
    public TestDefinition.HintPolicy getHintPolicy() { return hintPolicy; }
    public Instant getCreatedAt() { return createdAt; }
}
