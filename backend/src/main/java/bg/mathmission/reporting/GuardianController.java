package bg.mathmission.reporting;

import bg.mathmission.consent.ConsentService;
import bg.mathmission.consent.GuardianConsent;
import bg.mathmission.consent.PrivacyRequest;
import bg.mathmission.identity.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guardian")
@PreAuthorize("hasRole('GUARDIAN')")
@Tag(name = "Guardian")
public class GuardianController {

    private final GuardianService guardian;
    private final ConsentService consent;

    public GuardianController(GuardianService guardian, ConsentService consent) {
        this.guardian = guardian;
        this.consent = consent;
    }

    @GetMapping("/children")
    public List<GuardianService.ChildSummary> children() {
        return guardian.summaries(CurrentUser.id());
    }

    @PostMapping("/consents/{id}/withdraw")
    public Map<String, Object> withdraw(@PathVariable UUID id) {
        consent.withdraw(CurrentUser.id(), id);
        return Map.of("status", "WITHDRAWN", "consequences", bg.mathmission.consent.ConsentTexts.withdrawalConsequences());
    }

    public record Notifications(GuardianConsent.NotificationFrequency frequency) {}

    @PutMapping("/consents/{id}/notifications")
    public Map<String, String> notifications(@PathVariable UUID id, @RequestBody Notifications req) {
        consent.setNotifications(CurrentUser.id(), id, req.frequency());
        return Map.of("frequency", req.frequency().name());
    }

    @GetMapping("/consents/{id}/export")
    public Map<String, Object> export(@PathVariable UUID id) {
        consent.recordExport(CurrentUser.id(), id);
        return guardian.export(CurrentUser.id(), id);
    }

    @PostMapping("/consents/{id}/deletion-request")
    public Map<String, Object> deletion(@PathVariable UUID id) {
        PrivacyRequest r = consent.request(CurrentUser.id(), id, PrivacyRequest.Kind.DELETION);
        return Map.of("requestId", r.getId(), "status", r.getStatus(),
                "message", "Заявката е приета. Данните ще бъдат изтрити по документираната процедура и ще получите потвърждение.");
    }
}
