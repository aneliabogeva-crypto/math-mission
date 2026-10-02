package bg.mathmission.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Server-side session. Only a SHA-256 hash of the bearer token is stored. */
@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    private UUID id;
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected AuthSession() {}

    public AuthSession(String tokenHash, UUID userId, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
