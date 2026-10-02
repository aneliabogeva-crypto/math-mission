package bg.mathmission.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("delete from AuthSession s where s.userId = :userId")
    void deleteByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("delete from AuthSession s where s.expiresAt < :now")
    int deleteExpired(Instant now);
}
