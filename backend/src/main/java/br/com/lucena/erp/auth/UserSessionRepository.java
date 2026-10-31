package br.com.lucena.erp.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByRefreshTokenHash(String hash);

    @Query("select s from UserSession s where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now order by s.lastSeenAt desc")
    List<UserSession> findActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now, s.revokedReason = :reason where s.userId = :userId and s.revokedAt is null and (:keep is null or s.id <> :keep)")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("keep") UUID keepSessionId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now, s.revokedReason = :reason where s.facultyId = :facultyId and s.revokedAt is null")
    int revokeAllForFaculty(@Param("facultyId") UUID facultyId, @Param("now") Instant now, @Param("reason") String reason);

    @Query("select count(distinct s.userId) from UserSession s where s.revokedAt is null and s.expiresAt > :now")
    long countActiveUsers(@Param("now") Instant now);
}
