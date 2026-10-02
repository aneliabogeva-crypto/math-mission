package bg.mathmission.identity;

import bg.mathmission.consent.GuardianConsent;
import bg.mathmission.consent.GuardianConsentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Identity")
public class MeController {

    private final UserAccountRepository users;
    private final GuardianConsentRepository consents;
    private final SessionService sessions;

    public MeController(UserAccountRepository users, GuardianConsentRepository consents, SessionService sessions) {
        this.users = users;
        this.consents = consents;
        this.sessions = sessions;
    }

    /** Works for every role, including students whose activation is waiting for guardian consent. */
    @GetMapping
    public Map<String, Object> me() {
        CurrentUser cu = CurrentUser.get();
        UserAccount u = users.findById(cu.id()).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.getId());
        out.put("role", u.getRole());
        out.put("status", u.getStatus());
        out.put("displayName", u.getNickname());
        out.put("avatar", u.getAvatar());
        out.put("mfa", u.getTotpSecret() != null);
        if (u.getRole() == Role.STUDENT && u.getStatus() != AccountStatus.ACTIVE) {
            consents.findByStudentIdOrderByCreatedAtDesc(u.getId()).stream().findFirst().ifPresent(c -> {
                out.put("consentCode", c.getStatus() == GuardianConsent.Status.PENDING ? c.getConsentCode() : null);
                out.put("consentStatus", c.getStatus());
            });
        }
        return out;
    }

    @PostMapping("/logout")
    public Map<String, String> logout() {
        sessions.closeAll(CurrentUser.currentId());
        return Map.of("status", "ok");
    }
}
