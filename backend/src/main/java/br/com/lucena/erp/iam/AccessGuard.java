package br.com.lucena.erp.iam;

import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.security.AuthPrincipal;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Impede escalonamento de privilégios: só é possível conceder permissões que o próprio operador possui. */
@Component
public class AccessGuard {
    public void ensureCanGrant(AuthPrincipal actor, Collection<Role> roles) {
        if (actor.platformAdmin()) return;
        Set<String> missing = new TreeSet<>();
        roles.forEach(r -> r.getPermissions().forEach(p -> {
            if (!actor.permissions().contains(p.getCode())) missing.add(p.getCode());
        }));
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("Você não pode conceder permissões que não possui: " + String.join(", ", missing));
        }
    }

    public void ensureCanGrantCodes(AuthPrincipal actor, Collection<String> codes) {
        if (actor.platformAdmin()) return;
        Set<String> missing = new TreeSet<>(codes);
        missing.removeAll(actor.permissions());
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("Você não pode conceder permissões que não possui: " + String.join(", ", missing));
        }
    }
}
