package br.com.lucena.erp.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_authenticator_secrets")
@Getter
@Setter
public class AuthenticatorSecret {
    public enum Status { PENDING, ACTIVE, REVOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    /** Segredo TOTP cifrado com AES-256-GCM. Nunca exposto após a configuração. */
    private String secretCiphertext;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Long lastUsedStep;
    private Instant createdAt = Instant.now();
    private Instant activatedAt;
    private Instant revokedAt;
}
