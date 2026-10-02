package bg.mathmission.consent;

import bg.mathmission.identity.CurrentUser;
import bg.mathmission.identity.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public part of the guardian consent workflow (reached via the code the student shows). */
@RestController
@RequestMapping("/api/consent")
@Tag(name = "Consent")
public class ConsentController {

    private final ConsentService consent;
    private final bg.mathmission.common.AppProperties props;

    public ConsentController(ConsentService consent, bg.mathmission.common.AppProperties props) {
        this.consent = consent;
        this.props = props;
    }

    @GetMapping("/text/{audience}")
    public ConsentTexts.ConsentText text(@PathVariable String audience) {
        String v = props.consent().textVersion();
        return "child".equalsIgnoreCase(audience) ? ConsentTexts.child(v) : ConsentTexts.adult(v);
    }

    @GetMapping("/text/withdrawal-consequences")
    public List<String> consequences() {
        return ConsentTexts.withdrawalConsequences();
    }

    @GetMapping("/code/{code}")
    public ConsentService.CodeLookup lookup(@PathVariable String code) {
        return consent.lookup(code);
    }

    public record GrantRequest(String acceptedVersion, String username, String password) {}

    @PostMapping("/code/{code}/grant")
    public Map<String, Object> grant(@PathVariable String code, @RequestBody GrantRequest req) {
        UUID guardian = null;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu && cu.role() == Role.GUARDIAN) {
            guardian = cu.id();
        }
        ConsentService.GrantResult r = consent.grant(code, req.acceptedVersion(), guardian, req.username(), req.password());
        return r.token() == null ? Map.of("status", "GRANTED") : Map.of("status", "GRANTED", "token", r.token());
    }
}
