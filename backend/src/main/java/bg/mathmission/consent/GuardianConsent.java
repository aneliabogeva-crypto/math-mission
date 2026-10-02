package bg.mathmission.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Consent record: version, date, scope and verification status are always retained. */
@Entity
@Table(name = "guardian_consent")
public class GuardianConsent {

    public enum Status { PENDING, GRANTED, WITHDRAWN }

    public enum NotificationFrequency { WEEKLY, MONTHLY, OFF }

    @Id
    private UUID id;
    @Column(name = "student_id")
    private UUID studentId;
    @Column(name = "guardian_id")
    private UUID guardianId;
    @Column(name = "consent_code")
    private String consentCode;
    @Column(name = "text_version")
    private String textVersion;
    private String scope;
    @Enumerated(EnumType.STRING)
    private Status status;
    @Column(name = "verification_status")
    private String verificationStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_freq")
    private NotificationFrequency notificationFrequency = NotificationFrequency.WEEKLY;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "granted_at")
    private Instant grantedAt;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected GuardianConsent() {}

    public GuardianConsent(UUID studentId, String consentCode, String textVersion, String scope) {
        this.id = UUID.randomUUID();
        this.studentId = studentId;
        this.consentCode = consentCode;
        this.textVersion = textVersion;
        this.scope = scope;
        this.status = Status.PENDING;
        this.verificationStatus = "NOT_VERIFIED";
        this.createdAt = Instant.now();
    }

    public void grant(UUID guardianId, String verificationStatus) {
        this.guardianId = guardianId;
        this.status = Status.GRANTED;
        this.verificationStatus = verificationStatus;
        this.grantedAt = Instant.now();
    }

    public void withdraw() {
        this.status = Status.WITHDRAWN;
        this.withdrawnAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public UUID getGuardianId() { return guardianId; }
    public String getConsentCode() { return consentCode; }
    public String getTextVersion() { return textVersion; }
    public String getScope() { return scope; }
    public Status getStatus() { return status; }
    public String getVerificationStatus() { return verificationStatus; }
    public NotificationFrequency getNotificationFrequency() { return notificationFrequency; }
    public void setNotificationFrequency(NotificationFrequency f) { this.notificationFrequency = f; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
