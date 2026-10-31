package br.com.lucena.erp.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {
    Optional<RecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    /** Consumo atômico: retorna 1 somente para a primeira requisição que usar o código. */
    @Modifying
    @Query("update RecoveryCode c set c.usedAt = :now where c.id = :id and c.usedAt is null")
    int consume(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteAllForUser(@Param("userId") UUID userId);
}
