package br.com.lucena.erp.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID facultyId;
    private UUID actorId;
    private String actorName;
    private String action;
    private String entityType;
    private String entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;
    private String ipAddress;
    private Instant createdAt = Instant.now();
}
