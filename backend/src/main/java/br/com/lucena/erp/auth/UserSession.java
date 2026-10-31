package br.com.lucena.erp.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Getter
@Setter
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    private UUID facultyId;
    private String refreshTokenHash;
    private String ipAddress;
    private String userAgent;
    private String deviceLabel;
    private Instant createdAt = Instant.now();
    private Instant lastSeenAt = Instant.now();
    private Instant expiresAt;
    private Instant revokedAt;
    private String revokedReason;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
