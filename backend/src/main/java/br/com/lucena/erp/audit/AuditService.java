package br.com.lucena.erp.audit;

import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Registro de auditoria (append-only) e de acessos. Nunca registrar senhas, segredos TOTP,
 * códigos de recuperação ou tokens nos detalhes.
 */
@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditRepo;
    private final AccessLogRepository accessRepo;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuthPrincipal actor, UUID facultyId, String action, String entityType, Object entityId,
                       Map<String, Object> details, ClientInfo client) {
        AuditLog log = new AuditLog();
        log.setFacultyId(facultyId);
        if (actor != null) {
            log.setActorId(actor.userId());
            log.setActorName(actor.username());
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : entityId.toString());
        log.setDetails(details);
        log.setIpAddress(client == null ? null : client.ip());
        auditRepo.save(log);
    }

    /** Registro de evento do próprio usuário (ex.: troca de senha), sem principal em contexto. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSelf(UUID userId, String username, UUID facultyId, String action, Map<String, Object> details, ClientInfo client) {
        AuditLog log = new AuditLog();
        log.setFacultyId(facultyId);
        log.setActorId(userId);
        log.setActorName(username);
        log.setAction(action);
        log.setEntityType("USER");
        log.setEntityId(userId.toString());
        log.setDetails(details);
        log.setIpAddress(client == null ? null : client.ip());
        auditRepo.save(log);
    }

    /** Log de acesso em transação própria: falhas de login também precisam ser persistidas. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void access(UUID userId, UUID facultyId, String identifier, String event, boolean success, String detail, ClientInfo client) {
        AccessLog a = new AccessLog();
        a.setUserId(userId);
        a.setFacultyId(facultyId);
        a.setIdentifier(identifier == null ? null : identifier.substring(0, Math.min(identifier.length(), 120)));
        a.setEvent(event);
        a.setSuccess(success);
        a.setDetail(detail);
        if (client != null) {
            a.setIpAddress(client.ip());
            a.setUserAgent(client.userAgent());
        }
        accessRepo.save(a);
    }
}
