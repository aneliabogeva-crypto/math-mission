package bg.mathmission.reporting;

import bg.mathmission.assessment.TestBlueprint;
import bg.mathmission.assessment.TestBlueprintRepository;
import bg.mathmission.audit.AuditLog;
import bg.mathmission.audit.AuditLogRepository;
import bg.mathmission.audit.AuditService;
import bg.mathmission.common.ApiException;
import bg.mathmission.consent.PrivacyRequest;
import bg.mathmission.consent.PrivacyRequestRepository;
import bg.mathmission.identity.Role;
import bg.mathmission.identity.SessionService;
import bg.mathmission.identity.Totp;
import bg.mathmission.identity.UserAccount;
import bg.mathmission.identity.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administration: least-privilege staff accounts, access reviews, assessment models, privacy queue. */
@Service
public class AdminService {

    static final Duration INACTIVE_AFTER = Duration.ofDays(90);

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AuditLogRepository auditLog;
    private final TestBlueprintRepository blueprints;
    private final PrivacyRequestRepository privacy;
    private final SessionService sessions;

    public AdminService(UserAccountRepository users, PasswordEncoder encoder, AuditService audit, AuditLogRepository auditLog,
                        TestBlueprintRepository blueprints, PrivacyRequestRepository privacy, SessionService sessions) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.auditLog = auditLog;
        this.blueprints = blueprints;
        this.privacy = privacy;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------ staff and access review

    public record StaffView(UUID id, String username, String displayName, Role role, String status, boolean mfaEnabled,
                            Instant createdAt, Instant lastActiveAt, List<String> flags) {}

    public record NewStaff(UUID id, String username, Role role, String totpSecret) {}

    @Transactional
    public NewStaff createStaff(UUID adminId, Role role, String username, String displayName, String password) {
        if (role == Role.STUDENT || role == Role.GUARDIAN) {
            throw ApiException.badRequest("ROLE", "Учениците и родителите се регистрират сами.");
        }
        if (password == null || password.length() < 12) throw ApiException.badRequest("PASSWORD", "Паролата трябва да е поне 12 знака.");
        if (users.findByUsername(username).isPresent()) throw ApiException.conflict("USERNAME_TAKEN", "Потребителското име е заето.");
        UserAccount u = UserAccount.staff(role, username, displayName, encoder.encode(password));
        String secret = null;
        if (role == Role.ADMIN) {
            secret = Totp.newSecret(); // administrators always have MFA
            u.setTotpSecret(secret);
        }
        users.save(u);
        audit.record(adminId, "STAFF_CREATED", "UserAccount", u.getId(), "role=" + role);
        return new NewStaff(u.getId(), username, role, secret);
    }

    /** Access review: flags inactive privileged accounts and admins without MFA. */
    @Transactional(readOnly = true)
    public List<StaffView> accessReview() {
        Instant cutoff = Instant.now().minus(INACTIVE_AFTER);
        return users.findByRoleIn(List.of(Role.TEACHER, Role.AUTHOR, Role.REVIEWER, Role.ADMIN)).stream().map(u -> {
            List<String> flags = new ArrayList<>();
            Instant last = u.getLastActiveAt() == null ? u.getCreatedAt() : u.getLastActiveAt();
            if (last.isBefore(cutoff)) flags.add("Неактивен над 90 дни");
            if (u.getRole() == Role.ADMIN && u.getTotpSecret() == null) flags.add("Администратор без MFA");
            return new StaffView(u.getId(), u.getUsername(), u.getNickname(), u.getRole(), u.getStatus().name(),
                    u.getTotpSecret() != null, u.getCreatedAt(), u.getLastActiveAt(), flags);
        }).toList();
    }

    @Transactional
    public void disable(UUID adminId, UUID userId) {
        UserAccount u = users.findById(userId).orElseThrow(() -> ApiException.notFound("Профил"));
        if (u.getId().equals(adminId)) throw ApiException.badRequest("SELF", "Не можете да деактивирате собствения си профил.");
        u.setStatus(bg.mathmission.identity.AccountStatus.RESTRICTED);
        sessions.closeAll(u.getId());
        audit.record(adminId, "ACCOUNT_DISABLED", "UserAccount", u.getId(), "role=" + u.getRole());
    }

    public List<AuditLog> recentAudit() {
        return auditLog.findTop200ByOrderByCreatedAtDesc();
    }

    // ------------------------------------------------------------------ assessment models (US-ADM-02)

    public record BlueprintView(UUID id, String name, String academicYear, int version, String status,
                                Map<String, Integer> composition, int questionCount, int timeLimitMin) {}

    @Transactional
    public BlueprintView createBlueprint(UUID adminId, String name, String academicYear, Map<String, Integer> composition, int timeLimitMin) {
        int version = (int) blueprints.findAll().stream()
                .filter(b -> b.getName().equals(name) && b.getAcademicYear().equals(academicYear)).count() + 1;
        TestBlueprint b;
        try {
            b = blueprints.save(new TestBlueprint(name, academicYear, version, composition, timeLimitMin));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BLUEPRINT", e.getMessage());
        }
        audit.record(adminId, "BLUEPRINT_CREATED", "TestBlueprint", b.getId(), name + " v" + version);
        return view(b);
    }

    /** Approval only changes which model new tests may use; historical attempts are never altered. */
    @Transactional
    public BlueprintView approveBlueprint(UUID adminId, UUID id) {
        TestBlueprint b = blueprints.findById(id).orElseThrow(() -> ApiException.notFound("Модел"));
        b.setStatus(TestBlueprint.Status.APPROVED);
        audit.record(adminId, "BLUEPRINT_APPROVED", "TestBlueprint", b.getId(), b.getName() + " v" + b.getVersion());
        return view(b);
    }

    public List<BlueprintView> blueprints() {
        return blueprints.findAllByOrderByAcademicYearDescVersionDesc().stream().map(AdminService::view).toList();
    }

    static BlueprintView view(TestBlueprint b) {
        return new BlueprintView(b.getId(), b.getName(), b.getAcademicYear(), b.getVersion(), b.getStatus().name(),
                b.composition(), b.getQuestionCount(), b.getTimeLimitMin());
    }

    // ------------------------------------------------------------------ privacy requests

    public List<PrivacyRequest> openPrivacyRequests() {
        return privacy.findByStatusOrderByCreatedAtAsc(PrivacyRequest.Status.OPEN);
    }

    /** Completes a request; deletion and post-withdrawal retention erase the student's personal data. */
    @Transactional
    public void completePrivacyRequest(UUID adminId, UUID requestId) {
        PrivacyRequest r = privacy.findById(requestId).orElseThrow(() -> ApiException.notFound("Заявка"));
        if (r.getKind() == PrivacyRequest.Kind.DELETION || r.getKind() == PrivacyRequest.Kind.RETENTION_REVIEW) {
            users.findById(r.getStudentId()).ifPresent(UserAccount::anonymise);
            sessions.closeAll(r.getStudentId());
        }
        r.complete();
        audit.record(adminId, "PRIVACY_REQUEST_COMPLETED", "PrivacyRequest", r.getId(), r.getKind().name());
    }
}
