package bg.mathmission.classroom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "class_group")
public class ClassGroup {

    @Id
    private UUID id;
    @Column(name = "teacher_id")
    private UUID teacherId;
    private String name;
    private String code;
    @Column(name = "code_revoked")
    private boolean codeRevoked;
    @Column(name = "created_at")
    private Instant createdAt;

    protected ClassGroup() {}

    public ClassGroup(UUID teacherId, String name, String code) {
        this.id = UUID.randomUUID();
        this.teacherId = teacherId;
        this.name = name;
        this.code = code;
        this.createdAt = Instant.now();
    }

    public void replaceCode(String newCode) {
        this.code = newCode;
        this.codeRevoked = false;
    }

    public void revokeCode() {
        this.codeRevoked = true;
    }

    public UUID getId() { return id; }
    public UUID getTeacherId() { return teacherId; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public boolean isCodeRevoked() { return codeRevoked; }
    public Instant getCreatedAt() { return createdAt; }
}
