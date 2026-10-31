package br.com.lucena.erp.audit;

import br.com.lucena.erp.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registra na auditoria da faculdade quando um administrador da plataforma (Master) acessa os dados dela
 * para suporte. Para não gerar um registro por requisição, registra no máximo uma vez a cada 30 minutos
 * por sessão e faculdade. A faculdade vê esses registros na própria auditoria (transparência/LGPD).
 */
@Component
@RequiredArgsConstructor
public class SupportAccessLogger {
    static final Duration WINDOW = Duration.ofMinutes(30);

    private final AuditLogRepository repo;
    private final Clock clock;
    private final Map<String, Instant> lastLogged = new ConcurrentHashMap<>();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuthPrincipal admin, UUID facultyId) {
        Instant now = clock.instant();
        String key = admin.sessionId() + "|" + facultyId;
        Instant last = lastLogged.get(key);
        if (last != null && last.plus(WINDOW).isAfter(now)) return;
        lastLogged.put(key, now);
        if (lastLogged.size() > 10_000) lastLogged.entrySet().removeIf(e -> e.getValue().plus(WINDOW).isBefore(now));

        String path = null, ip = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            path = req.getMethod() + " " + req.getRequestURI();
            ip = req.getRemoteAddr();
        }
        AuditLog a = new AuditLog();
        a.setFacultyId(facultyId);
        a.setActorId(admin.userId());
        a.setActorName(admin.username());
        a.setAction("PLATFORM_SUPPORT_ACCESS");
        a.setEntityType("FACULTY");
        a.setEntityId(facultyId.toString());
        a.setDetails(Map.of("firstRequest", path == null ? "" : path, "windowMinutes", WINDOW.toMinutes()));
        a.setIpAddress(ip);
        repo.save(a);
    }
}
