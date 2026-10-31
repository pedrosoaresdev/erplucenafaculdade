package br.com.lucena.erp.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_logs")
@Getter
@Setter
public class AccessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID userId;
    private UUID facultyId;
    private String identifier;
    private String event;
    private boolean success;
    private String ipAddress;
    private String userAgent;
    private String detail;
    private Instant createdAt = Instant.now();
}
