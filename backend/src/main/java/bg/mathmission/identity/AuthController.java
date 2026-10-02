package bg.mathmission.identity;

import bg.mathmission.audit.AuditService;
import bg.mathmission.common.ApiException;
import bg.mathmission.common.Codes;
import bg.mathmission.consent.ConsentService;
import bg.mathmission.consent.GuardianConsent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Identity")
public class AuthController {

    public static final java.util.Set<String> AVATARS = java.util.Set.of(
            "fox", "owl", "rocket", "cat", "robot", "planet", "dragon", "turtle");

    private final UserAccountRepository users;
    private final SessionService sessions;
    private final ConsentService consent;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AuthController(UserAccountRepository users, SessionService sessions, ConsentService consent,
                          PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.consent = consent;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** Only a nickname is required. No full name, phone number, photo or date of birth. */
    public record StudentSignup(
            @NotBlank @Size(min = 2, max = 24)
            @Pattern(regexp = "^[\\p{L}\\p{N} _.-]+$", message = "Само букви, цифри, интервал, _ . -")
            String nickname,
            @NotBlank String avatar,
            @NotNull UserAccount.AgeBand ageBand,
            @NotNull UserAccount.LearningGoal goal,
            @Min(1) @Max(5) int confidence) {}

    public record StudentSignupResult(String token, String recoveryCode, String status, String consentCode) {}

    @PostMapping("/student")
    @Operation(summary = "Create a nickname-based student profile (US-STU-01)")
    @Transactional
    public StudentSignupResult signup(@Valid @RequestBody StudentSignup req) {
        if (!AVATARS.contains(req.avatar())) {
            throw ApiException.badRequest("AVATAR", "Избери аватар от списъка.");
        }
        UserAccount s = UserAccount.student(req.nickname().trim(), req.avatar(), req.ageBand(), req.goal(), req.confidence());
        String handle = Codes.generate(6);
        String secret = Codes.generate(10);
        s.setRecovery(handle, encoder.encode(secret));
        String consentCode = null;
        if (consent.consentRequired(req.ageBand())) {
            s.setStatus(AccountStatus.PENDING_CONSENT);
        }
        users.save(s);
        if (s.getStatus() == AccountStatus.PENDING_CONSENT) {
            GuardianConsent c = consent.startFor(s);
            consentCode = c.getConsentCode();
        }
        audit.record(s.getId(), "STUDENT_CREATED", "UserAccount", s.getId(), "status=" + s.getStatus());
        return new StudentSignupResult(sessions.open(s.getId()), handle + "-" + secret, s.getStatus().name(), consentCode);
    }

    public record RecoverRequest(@NotBlank @Size(max = 40) String recoveryCode) {}

    public record LoginResult(String token, String role, String status) {}

    @PostMapping("/student/recover")
    @Operation(summary = "Sign in on another device with the student's recovery code")
    @Transactional
    public LoginResult recover(@Valid @RequestBody RecoverRequest req) {
        String[] parts = req.recoveryCode().trim().toUpperCase().split("-");
        if (parts.length != 2) throw invalidLogin();
        UserAccount u = users.findByUsername("s:" + parts[0]).orElseThrow(AuthController::invalidLogin);
        if (!encoder.matches(parts[1], u.getPasswordHash())) throw invalidLogin();
        if (u.getStatus() == AccountStatus.DELETED) throw invalidLogin();
        u.touch();
        return new LoginResult(sessions.open(u.getId()), u.getRole().name(), u.getStatus().name());
    }

    public record LoginRequest(@NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 200) String password,
                               @Size(max = 6) String totp) {}

    @PostMapping("/login")
    @Operation(summary = "Staff and guardian login; administrators must supply a TOTP code (MFA)")
    @Transactional(noRollbackFor = ApiException.class) // keep the MFA_FAILED audit record
    public LoginResult login(@Valid @RequestBody LoginRequest req) {
        UserAccount u = users.findByUsername(req.username()).orElseThrow(AuthController::invalidLogin);
        if (u.getRole() == Role.STUDENT || u.getPasswordHash() == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw invalidLogin();
        }
        if (u.getRole() == Role.ADMIN) {
            if (u.getTotpSecret() == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "MFA_NOT_CONFIGURED", "Администраторският профил няма настроен втори фактор.");
            }
            if (req.totp() == null || req.totp().isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "MFA_REQUIRED", "Въведете кода от приложението за удостоверяване.");
            }
            if (!Totp.verify(u.getTotpSecret(), req.totp(), Instant.now().getEpochSecond())) {
                audit.record(u.getId(), "MFA_FAILED", "UserAccount", u.getId(), null);
                throw invalidLogin();
            }
        }
        u.touch();
        audit.record(u.getId(), "LOGIN", "UserAccount", u.getId(), "role=" + u.getRole());
        return new LoginResult(sessions.open(u.getId()), u.getRole().name(), u.getStatus().name());
    }

    private static ApiException invalidLogin() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN", "Невалидни данни за вход.");
    }
}
