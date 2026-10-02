package bg.mathmission.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves "Authorization: Bearer &lt;token&gt;" to a {@link CurrentUser}. Authorities:
 * ROLE_&lt;role&gt; for active accounts; pending/restricted students only get ROLE_PENDING_STUDENT.
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private final SessionService sessions;
    private final UserAccountRepository users;

    public TokenAuthFilter(SessionService sessions, UserAccountRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            sessions.resolve(header.substring(7).trim())
                    .flatMap(users::findById)
                    .filter(u -> u.getStatus() != AccountStatus.DELETED)
                    .ifPresent(u -> {
                        String authority = u.getStatus() == AccountStatus.ACTIVE
                                ? "ROLE_" + u.getRole().name()
                                : "ROLE_PENDING_" + u.getRole().name();
                        var auth = new UsernamePasswordAuthenticationToken(
                                new CurrentUser(u.getId(), u.getRole(), u.getStatus()), null,
                                List.of(new SimpleGrantedAuthority(authority)));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
