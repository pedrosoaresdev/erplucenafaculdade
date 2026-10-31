package br.com.lucena.erp.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface AuthenticatorSecretRepository extends JpaRepository<AuthenticatorSecret, UUID> {
    Optional<AuthenticatorSecret> findByUserIdAndStatus(UUID userId, AuthenticatorSecret.Status status);

    /** Bloqueio pessimista: evita que o mesmo código seja aceito em duas requisições concorrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthenticatorSecret s where s.userId = :userId and s.status = :status")
    Optional<AuthenticatorSecret> lockByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") AuthenticatorSecret.Status status);
}
