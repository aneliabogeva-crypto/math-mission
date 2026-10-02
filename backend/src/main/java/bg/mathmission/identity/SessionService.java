package bg.mathmission.identity;

import bg.mathmission.common.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthSessionRepository sessions;
    private final AppProperties props;

    public SessionService(AuthSessionRepository sessions, AppProperties props) {
        this.sessions = sessions;
        this.props = props;
    }

    /** Creates a session and returns the raw bearer token (shown to the client exactly once). */
    @Transactional
    public String open(UUID userId) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        sessions.save(new AuthSession(hash(token), userId, Instant.now().plus(Duration.ofHours(props.session().ttlHours()))));
        return token;
    }

    public Optional<UUID> resolve(String token) {
        return sessions.findByTokenHash(hash(token))
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .map(AuthSession::getUserId);
    }

    public void closeAll(UUID userId) {
        sessions.deleteByUserId(userId);
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpired() {
        sessions.deleteExpired(Instant.now());
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
