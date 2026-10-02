package bg.mathmission.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Earned reward. Rewards are deterministic (never chance-based), never purchased and never
 * removed once earned.
 */
@Entity
@Immutable
@Table(name = "reward")
public class Reward {

    @Id
    private UUID id;
    @Column(name = "student_id")
    private UUID studentId;
    private String code;
    private String title;
    private int xp;
    @Column(name = "source_ref")
    private String sourceRef;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Reward() {}

    public Reward(UUID studentId, String code, String title, int xp, String sourceRef) {
        this.id = UUID.randomUUID();
        this.studentId = studentId;
        this.code = code;
        this.title = title;
        this.xp = xp;
        this.sourceRef = sourceRef;
        this.createdAt = Instant.now();
    }

    public UUID getStudentId() { return studentId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public int getXp() { return xp; }
    public String getSourceRef() { return sourceRef; }
    public Instant getCreatedAt() { return createdAt; }
}
