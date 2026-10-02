package bg.mathmission.content;

import bg.mathmission.common.Json;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson")
public class Lesson {

    @Id
    private UUID id;
    @Column(name = "lesson_key")
    private String lessonKey;
    private int version;
    @Enumerated(EnumType.STRING)
    private ContentStatus status;
    private String path;
    private String title;
    @Column(name = "learning_outcome")
    private String learningOutcome;
    @Column(name = "academic_year")
    private String academicYear;
    @Column(name = "content_json")
    private String contentJson;
    @Column(name = "author_id")
    private UUID authorId;
    @Column(name = "reviewer_id")
    private UUID reviewerId;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_comment")
    private String reviewComment;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Lesson() {}

    public Lesson(String lessonKey, int version, String path, String title, String learningOutcome,
                  String academicYear, LessonModel content, UUID authorId) {
        this.id = UUID.randomUUID();
        this.lessonKey = lessonKey;
        this.version = version;
        this.status = ContentStatus.DRAFT;
        this.path = path;
        this.title = title;
        this.learningOutcome = learningOutcome;
        this.academicYear = academicYear;
        this.contentJson = Json.write(content);
        this.authorId = authorId;
        this.createdAt = Instant.now();
    }

    public LessonModel content() {
        return Json.read(contentJson, LessonModel.class);
    }

    public void setContent(String title, LessonModel content) {
        this.title = title;
        this.contentJson = Json.write(content);
    }

    public void markReviewed(UUID reviewerId, String comment) {
        this.reviewerId = reviewerId;
        this.reviewedAt = Instant.now();
        this.reviewComment = comment;
    }

    public UUID getId() { return id; }
    public String getLessonKey() { return lessonKey; }
    public int getVersion() { return version; }
    public ContentStatus getStatus() { return status; }
    public void setStatus(ContentStatus status) { this.status = status; }
    public String getPath() { return path; }
    public String getTitle() { return title; }
    public String getLearningOutcome() { return learningOutcome; }
    public String getAcademicYear() { return academicYear; }
    public UUID getAuthorId() { return authorId; }
    public UUID getReviewerId() { return reviewerId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String c) { this.reviewComment = c; }
    public Instant getCreatedAt() { return createdAt; }
}
