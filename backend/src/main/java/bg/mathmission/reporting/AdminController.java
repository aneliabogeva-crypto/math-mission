package bg.mathmission.reporting;

import bg.mathmission.assessment.TestAuthoringService;
import bg.mathmission.audit.AuditLog;
import bg.mathmission.consent.PrivacyRequest;
import bg.mathmission.identity.CurrentUser;
import bg.mathmission.identity.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final AdminService admin;
    private final TestAuthoringService authoring;

    public AdminController(AdminService admin, TestAuthoringService authoring) {
        this.admin = admin;
        this.authoring = authoring;
    }

    public record StaffRequest(Role role, String username, String displayName, String password) {}

    @PostMapping("/staff")
    public AdminService.NewStaff createStaff(@RequestBody StaffRequest req) {
        return admin.createStaff(CurrentUser.id(), req.role(), req.username(), req.displayName(), req.password());
    }

    @GetMapping("/access-review")
    public List<AdminService.StaffView> accessReview() {
        return admin.accessReview();
    }

    @PostMapping("/users/{id}/disable")
    public Map<String, String> disable(@PathVariable UUID id) {
        admin.disable(CurrentUser.id(), id);
        return Map.of("status", "RESTRICTED");
    }

    @GetMapping("/audit")
    public List<AuditLog> audit() {
        return admin.recentAudit();
    }

    public record BlueprintRequest(String name, String academicYear, Map<String, Integer> composition, int timeLimitMin) {}

    @GetMapping("/assessment-models")
    public List<AdminService.BlueprintView> blueprints() {
        return admin.blueprints();
    }

    @PostMapping("/assessment-models")
    public AdminService.BlueprintView createBlueprint(@RequestBody BlueprintRequest req) {
        return admin.createBlueprint(CurrentUser.id(), req.name(), req.academicYear(), req.composition(), req.timeLimitMin());
    }

    @PostMapping("/assessment-models/{id}/approve")
    public AdminService.BlueprintView approve(@PathVariable UUID id) {
        return admin.approveBlueprint(CurrentUser.id(), id);
    }

    @PostMapping("/tests/{id}/publish")
    public TestAuthoringService.Preview publishTest(@PathVariable UUID id) {
        return authoring.publish(CurrentUser.id(), id, true);
    }

    @GetMapping("/privacy-requests")
    public List<PrivacyRequest> privacyRequests() {
        return admin.openPrivacyRequests();
    }

    @PostMapping("/privacy-requests/{id}/complete")
    public Map<String, String> complete(@PathVariable UUID id) {
        admin.completePrivacyRequest(CurrentUser.id(), id);
        return Map.of("status", "COMPLETED");
    }
}
