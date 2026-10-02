package bg.mathmission.consent;

import bg.mathmission.audit.AuditService;
import bg.mathmission.common.ApiException;
import bg.mathmission.common.AppProperties;
import bg.mathmission.common.Codes;
import bg.mathmission.identity.AccountStatus;
import bg.mathmission.identity.Role;
import bg.mathmission.identity.SessionService;
import bg.mathmission.identity.UserAccount;
import bg.mathmission.identity.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

    /**
     * Verification method used by the MVP. A verifiable method (e.g. school confirmation or eID)
     * must be selected with the account-model decision before production (spec section 14).
     */
    public static final String VERIFICATION_MVP = "GUARDIAN_ACCOUNT_SELF_DECLARED";

    private final GuardianConsentRepository consents;
    private final PrivacyRequestRepository privacyRequests;
    private final UserAccountRepository users;
    private final SessionService sessions;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties props;

    public ConsentService(GuardianConsentRepository consents, PrivacyRequestRepository privacyRequests,
                          UserAccountRepository users, SessionService sessions, PasswordEncoder encoder,
                          AuditService audit, AppProperties props) {
        this.consents = consents;
        this.privacyRequests = privacyRequests;
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
    }

    public boolean consentRequired(UserAccount.AgeBand band) {
        return band == UserAccount.AgeBand.UNDER_14 && props.consent().requiredUnderAge() > 0;
    }

    @Transactional
    public GuardianConsent startFor(UserAccount student) {
        GuardianConsent c = new GuardianConsent(student.getId(), Codes.generate(8), props.consent().textVersion(), ConsentTexts.SCOPE);
        consents.save(c);
        audit.record(student.getId(), "CONSENT_REQUESTED", "GuardianConsent", c.getId(), "version=" + c.getTextVersion());
        return c;
    }

    public record CodeLookup(String studentNickname, GuardianConsent.Status status, String textVersion, String scope) {}

    public CodeLookup lookup(String code) {
        GuardianConsent c = byCode(code);
        String nick = users.findById(c.getStudentId()).map(UserAccount::getNickname).orElse("?");
        return new CodeLookup(nick, c.getStatus(), c.getTextVersion(), c.getScope());
    }

    public record GrantResult(UUID guardianId, String token) {}

    /**
     * Grants consent. If no guardian is logged in, a guardian account is created with the given
     * username/password. Activation of the student only happens here.
     */
    @Transactional
    public GrantResult grant(String code, String acceptedVersion, UUID loggedInGuardian, String username, String password) {
        GuardianConsent c = byCode(code);
        if (c.getStatus() == GuardianConsent.Status.GRANTED) {
            throw ApiException.conflict("ALREADY_GRANTED", "Съгласието вече е дадено.");
        }
        if (!c.getTextVersion().equals(acceptedVersion)) {
            throw ApiException.badRequest("VERSION_MISMATCH", "Текстът на съгласието е обновен. Моля, прочетете новата версия.");
        }
        UserAccount guardian;
        String token = null;
        if (loggedInGuardian != null) {
            guardian = users.findById(loggedInGuardian).filter(u -> u.getRole() == Role.GUARDIAN)
                    .orElseThrow(() -> ApiException.forbidden("Само родител/настойник може да даде съгласие."));
        } else {
            if (username == null || username.length() < 4 || password == null || password.length() < 10) {
                throw ApiException.badRequest("WEAK_CREDENTIALS", "Изберете потребителско име (поне 4 знака) и парола (поне 10 знака).");
            }
            if (users.findByUsername(username).isPresent()) {
                throw ApiException.conflict("USERNAME_TAKEN", "Това потребителско име е заето.");
            }
            guardian = users.save(UserAccount.staff(Role.GUARDIAN, username, "Родител", encoder.encode(password)));
            token = sessions.open(guardian.getId());
        }
        c.grant(guardian.getId(), VERIFICATION_MVP);
        UserAccount student = users.findById(c.getStudentId()).orElseThrow(() -> ApiException.notFound("Ученик"));
        student.setStatus(AccountStatus.ACTIVE);
        audit.record(guardian.getId(), "CONSENT_GRANTED", "GuardianConsent", c.getId(),
                "version=" + c.getTextVersion() + ";verification=" + VERIFICATION_MVP);
        return new GrantResult(guardian.getId(), token);
    }

    @Transactional
    public void withdraw(UUID guardianId, UUID consentId) {
        GuardianConsent c = ownedBy(guardianId, consentId);
        if (c.getStatus() != GuardianConsent.Status.GRANTED) {
            throw ApiException.conflict("NOT_GRANTED", "Няма активно съгласие за оттегляне.");
        }
        c.withdraw();
        users.findById(c.getStudentId()).ifPresent(s -> s.setStatus(AccountStatus.RESTRICTED));
        sessions.closeAll(c.getStudentId());
        privacyRequests.save(new PrivacyRequest(c.getStudentId(), guardianId, PrivacyRequest.Kind.RETENTION_REVIEW));
        audit.record(guardianId, "CONSENT_WITHDRAWN", "GuardianConsent", c.getId(), "retention workflow opened");
    }

    @Transactional
    public void setNotifications(UUID guardianId, UUID consentId, GuardianConsent.NotificationFrequency f) {
        ownedBy(guardianId, consentId).setNotificationFrequency(f);
    }

    @Transactional
    public PrivacyRequest request(UUID guardianId, UUID consentId, PrivacyRequest.Kind kind) {
        GuardianConsent c = ownedBy(guardianId, consentId);
        PrivacyRequest r = privacyRequests.save(new PrivacyRequest(c.getStudentId(), guardianId, kind));
        audit.record(guardianId, "PRIVACY_REQUEST_" + kind, "Student", c.getStudentId(), null);
        return r;
    }

    /** Exports are produced immediately; the request is recorded as completed for the audit trail. */
    @Transactional
    public void recordExport(UUID guardianId, UUID consentId) {
        PrivacyRequest r = request(guardianId, consentId, PrivacyRequest.Kind.EXPORT);
        r.complete();
    }

    public List<GuardianConsent> forGuardian(UUID guardianId) {
        return consents.findByGuardianId(guardianId);
    }

    public GuardianConsent ownedBy(UUID guardianId, UUID consentId) {
        GuardianConsent c = consents.findById(consentId).orElseThrow(() -> ApiException.notFound("Съгласие"));
        if (!guardianId.equals(c.getGuardianId())) {
            throw ApiException.forbidden("Това съгласие не е ваше.");
        }
        return c;
    }

    public void requireGuardianOf(UUID guardianId, UUID studentId) {
        boolean ok = consents.findByGuardianId(guardianId).stream()
                .anyMatch(c -> c.getStudentId().equals(studentId) && c.getStatus() != GuardianConsent.Status.PENDING);
        if (!ok) throw ApiException.forbidden("Нямате достъп до този профил.");
    }

    private GuardianConsent byCode(String code) {
        return consents.findByConsentCode(code == null ? "" : code.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CODE_NOT_FOUND", "Кодът не е намерен."));
    }
}
