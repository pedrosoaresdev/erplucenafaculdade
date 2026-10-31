package br.com.lucena.erp.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_codes")
@Getter
@Setter
public class RecoveryCode {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    /** SHA-256 do código (o código em claro é exibido uma única vez). */
    private String codeHash;
    private Instant usedAt;
    private Instant createdAt = Instant.now();
}
