package br.com.lucena.erp.security;

import br.com.lucena.erp.audit.SupportAccessLogger;
import br.com.lucena.erp.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolve a faculdade (tenant) de uma operação. A faculdade de usuários comuns vem SEMPRE da sessão
 * autenticada; um facultyId vindo do cliente só é aceito para administradores da plataforma.
 */
@Component
@RequiredArgsConstructor
public class TenantResolver {
    private final SupportAccessLogger supportAccess;

    public UUID resolve(AuthPrincipal principal, UUID requestedFacultyId) {
        if (principal.platformAdmin()) {
            if (requestedFacultyId == null) {
                throw ApiException.badRequest("FACULTY_REQUIRED", "Informe a faculdade (facultyId)");
            }
            supportAccess.log(principal, requestedFacultyId); // acesso de suporte auditado na faculdade
            return requestedFacultyId;
        }
        if (requestedFacultyId != null && !requestedFacultyId.equals(principal.facultyId())) {
            throw ApiException.forbidden("Acesso a dados de outra instituição não é permitido");
        }
        return principal.facultyId();
    }

    public UUID current() {
        return resolve(CurrentUser.get(), null);
    }
}
