package bg.mathmission.identity;

import bg.mathmission.common.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Authenticated principal, resolved server-side from the session token. */
public record CurrentUser(UUID id, Role role, AccountStatus status) {

    public static CurrentUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CurrentUser u) {
            return u;
        }
        throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Влез в профила си.");
    }

    public static UUID id() {
        return get().id();
    }
}
