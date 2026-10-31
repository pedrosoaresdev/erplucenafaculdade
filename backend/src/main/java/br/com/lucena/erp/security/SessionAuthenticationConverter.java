package br.com.lucena.erp.security;

import br.com.lucena.erp.auth.UserSession;
import br.com.lucena.erp.auth.UserSessionRepository;
import br.com.lucena.erp.iam.User;
import br.com.lucena.erp.iam.UserRepository;
import br.com.lucena.erp.platform.Faculty;
import br.com.lucena.erp.platform.FacultyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Converte um JWT válido em autenticação, validando no banco: sessão ativa (não revogada),
 * usuário ativo/desbloqueado, faculdade ativa e MFA configurado. Permissões são carregadas do banco
 * para que alterações e revogações tenham efeito imediato.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final FacultyRepository faculties;
    private final Clock clock;

    @Override
    @Transactional
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("typ"))) {
            throw new InvalidBearerTokenException("Tipo de token inválido");
        }
        Instant now = clock.instant();
        UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
        UUID userId = UUID.fromString(jwt.getSubject());
        UserSession session = sessions.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId) && s.isActive(now))
                .orElseThrow(() -> new InvalidBearerTokenException("Sessão encerrada"));
        User user = users.findById(userId)
                .filter(u -> u.getStatus() == User.Status.ACTIVE && !u.isLocked(now) && u.isMfaEnabled())
                .orElseThrow(() -> new InvalidBearerTokenException("Usuário inativo"));
        if (user.getFacultyId() != null) {
            Faculty f = faculties.findById(user.getFacultyId()).orElseThrow(() -> new InvalidBearerTokenException("Instituição inválida"));
            if (f.getStatus() != Faculty.Status.ACTIVE) {
                throw new InvalidBearerTokenException("Instituição suspensa");
            }
        }
        if (session.getLastSeenAt().plus(TOUCH_INTERVAL).isBefore(now)) {
            session.setLastSeenAt(now);
        }

        Set<String> perms = new TreeSet<>();
        // Usuário com troca de senha obrigatória não recebe permissões até concluir a troca.
        if (!user.isMustChangePassword()) {
            user.getRoles().forEach(r -> r.getPermissions().forEach(p -> perms.add(p.getCode())));
            if (user.isPlatformAdmin()) perms.add("PLATFORM_ADMIN");
        }
        AuthPrincipal principal = new AuthPrincipal(user.getId(), sessionId, user.getFacultyId(), user.getUsername(),
                user.getFullName(), user.isPlatformAdmin() && !user.isMustChangePassword(), user.isMustChangePassword(), Set.copyOf(perms));
        List<GrantedAuthority> authorities = new ArrayList<>();
        perms.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
    }
}
