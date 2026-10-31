package br.com.lucena.erp.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AuthChallenge c where c.tokenHash = :hash")
    Optional<AuthChallenge> lockByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from AuthChallenge c where c.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);

    @Modifying
    @Query("update AuthChallenge c set c.consumedAt = :now where c.userId = :userId and c.consumedAt is null")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
