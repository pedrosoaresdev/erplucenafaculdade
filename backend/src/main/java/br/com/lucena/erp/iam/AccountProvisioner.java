package br.com.lucena.erp.iam;

import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.crypto.Tokens;
import br.com.lucena.erp.platform.PlanLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cria contas de acesso com senha temporária (exibida uma única vez) e troca obrigatória no primeiro acesso,
 * quando também é exigida a configuração do Google Authenticator.
 */
@Service
@RequiredArgsConstructor
public class AccountProvisioner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final PlanLimits planLimits;

    public record Provisioned(User user, String temporaryPassword) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Provisioned create(UUID facultyId, String username, String fullName, String email, String phone, Set<Role> userRoles) {
        if (users.findInFaculty(facultyId, username).isPresent()) {
            throw ApiException.conflict("USERNAME_IN_USE", "Nome de usuário/matrícula já utilizado nesta instituição");
        }
        if (email == null || email.isBlank()) {
            throw ApiException.badRequest("EMAIL_REQUIRED", "Informe o e-mail para gerar o acesso");
        }
        planLimits.ensureCanAddUser(facultyId);
        String temp = Tokens.temporaryPassword();
        User u = new User();
        u.setFacultyId(facultyId);
        u.setUsername(username);
        u.setFullName(fullName.trim());
        u.setEmail(email.trim().toLowerCase());
        u.setPhone(phone);
        u.setPasswordHash(encoder.encode(temp));
        u.setMustChangePassword(true);
        u.setRoles(new HashSet<>(userRoles));
        users.save(u);
        return new Provisioned(u, temp);
    }

    /** Cargo padrão da faculdade (ex.: ALUNO, PROFESSOR). */
    public Role systemRole(UUID facultyId, String code) {
        return roles.findByFacultyIdAndCode(facultyId, code)
                .orElseThrow(() -> ApiException.conflict("ROLE_MISSING", "Cargo padrão " + code + " não encontrado nesta instituição"));
    }
}
