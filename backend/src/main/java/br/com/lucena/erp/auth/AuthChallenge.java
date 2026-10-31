package br.com.lucena.erp.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Desafio de curta duração: etapa intermediária do login (senha OK, falta TOTP) ou step-up. */
@Entity
@Table(name = "auth_challenges")
@Getter
@Setter
public class AuthChallenge {
    public enum Purpose {
        LOGIN_MFA, LOGIN_MFA_SETUP,
        STEPUP_PASSWORD_CHANGE, STEPUP_MFA_ROTATE, STEPUP_MFA_DISABLE, STEPUP_RECOVERY_CODES, STEPUP_PROFILE_SENSITIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    private String tokenHash;
    @Enumerated(EnumType.STRING)
    private Purpose purpose;
    private int attempts;
    private Instant expiresAt;
    private Instant consumedAt;
    private String ipAddress;
    private Instant createdAt = Instant.now();

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
