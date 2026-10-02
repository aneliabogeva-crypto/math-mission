package bg.mathmission.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Retained record of every review: reviewer identity, decision, version and timestamp. */
@Entity
@Immutable
@Table(name = "review_decision")
public class ReviewDecision {

    @Id
    private UUID id;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "content_id")
    private UUID contentId;
    @Column(name = "content_key")
    private String contentKey;
    private int version;
    @Column(name = "reviewer_id")
    private UUID reviewerId;
    private String decision;
    private String comment;
    @Column(name = "created_at")
    private Instant createdAt;

    protected ReviewDecision() {}

    public ReviewDecision(String contentType, UUID contentId, String contentKey, int version, UUID reviewerId,
                          String decision, String comment) {
        this.id = UUID.randomUUID();
        this.contentType = contentType;
        this.contentId = contentId;
        this.contentKey = contentKey;
        this.version = version;
        this.reviewerId = reviewerId;
        this.decision = decision;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public String getContentType() { return contentType; }
    public UUID getContentId() { return contentId; }
    public String getContentKey() { return contentKey; }
    public int getVersion() { return version; }
    public UUID getReviewerId() { return reviewerId; }
    public String getDecision() { return decision; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
