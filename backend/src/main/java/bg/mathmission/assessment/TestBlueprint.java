package bg.mathmission.assessment;

import bg.mathmission.common.Json;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned assessment model (US-ADM-02): academic year, composition by response type, timing.
 * Only APPROVED models may be used for new tests; existing attempts keep their own snapshot.
 */
@Entity
@Table(name = "test_blueprint")
public class TestBlueprint {

    public enum Status { DRAFT, APPROVED, RETIRED }

    @Id
    private UUID id;
    private String name;
    @Column(name = "academic_year")
    private String academicYear;
    private int version;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;
    @Column(name = "composition_json")
    private String compositionJson;
    @Column(name = "time_limit_min")
    private int timeLimitMin;
    @Column(name = "question_count")
    private int questionCount;
    @Column(name = "created_at")
    private Instant createdAt;

    protected TestBlueprint() {}

    public TestBlueprint(String name, String academicYear, int version, Map<String, Integer> composition, int timeLimitMin) {
        int count = composition.values().stream().mapToInt(Integer::intValue).sum();
        if (count < 20) throw new IllegalArgumentException("Оценяващ тест трябва да има поне 20 въпроса.");
        this.id = UUID.randomUUID();
        this.name = name;
        this.academicYear = academicYear;
        this.version = version;
        this.status = Status.DRAFT;
        this.compositionJson = Json.write(composition);
        this.timeLimitMin = timeLimitMin;
        this.questionCount = count;
        this.createdAt = Instant.now();
    }

    public Map<String, Integer> composition() {
        return Json.read(compositionJson, new TypeReference<Map<String, Integer>>() {});
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getAcademicYear() { return academicYear; }
    public int getVersion() { return version; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getTimeLimitMin() { return timeLimitMin; }
    public int getQuestionCount() { return questionCount; }
    public Instant getCreatedAt() { return createdAt; }
}
