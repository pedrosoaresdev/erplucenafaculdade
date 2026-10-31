package br.com.lucena.erp.security;

import java.util.Set;
import java.util.UUID;

/**
 * Identidade autenticada, montada a partir do banco a cada requisição (não confia em claims do token
 * para faculdade ou permissões). facultyId é null somente para administradores da plataforma.
 */
public record AuthPrincipal(UUID userId, UUID sessionId, UUID facultyId, String username, String fullName,
                            boolean platformAdmin, boolean mustChangePassword, Set<String> permissions) {

    public boolean has(String permission) {
        return platformAdmin || permissions.contains(permission);
    }
}
