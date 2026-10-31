package br.com.lucena.erp.security;

import br.com.lucena.erp.config.ErpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por IP (janela deslizante de 1 minuto) nos endpoints de autenticação e webhooks.
 * Em produção com várias instâncias, complementar com limitação no gateway/Redis.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final int limit;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(ErpProperties props) {
        this.limit = props.security().authRequestsPerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/auth/") || uri.startsWith("/api/me/step-up") || uri.startsWith("/api/setup/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(req.getRemoteAddr(), k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < now - 60_000) q.pollFirst();
            allowed = q.size() < limit;
            if (allowed) q.addLast(now);
        }
        if (!allowed) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"detail\":\"Muitas tentativas. Aguarde um minuto.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    /** Limpeza periódica para não acumular IPs inativos. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 300_000)
    public void evict() {
        long cutoff = System.currentTimeMillis() - 60_000;
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() < cutoff;
            }
        });
    }

    public void reset() {
        hits.clear();
    }
}
