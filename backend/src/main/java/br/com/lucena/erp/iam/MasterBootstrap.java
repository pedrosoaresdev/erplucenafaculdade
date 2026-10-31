package br.com.lucena.erp.iam;

import br.com.lucena.erp.config.ErpProperties;
import br.com.lucena.erp.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o primeiro administrador Master se ainda não existir nenhum e as variáveis
 * ERP_BOOTSTRAP_MASTER_* estiverem definidas. O Google Authenticator é configurado no primeiro login.
 */
@Slf4j
@Component
@org.springframework.core.annotation.Order(10)
@RequiredArgsConstructor
public class MasterBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final ErpProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var b = props.bootstrap();
        if (b == null || b.masterUsername() == null || b.masterUsername().isBlank() || users.existsByPlatformAdminTrue()) {
            return;
        }
        policy.validate(b.masterPassword(), null, b.masterUsername());
        User u = new User();
        u.setUsername(b.masterUsername());
        u.setFullName(b.masterName() == null ? "Administrador Master" : b.masterName());
        u.setEmail(b.masterEmail() == null ? "master@localhost" : b.masterEmail());
        u.setPasswordHash(encoder.encode(b.masterPassword()));
        u.setPlatformAdmin(true);
        users.save(u);
        log.info("Administrador Master '{}' criado. Configure o autenticador no primeiro login.", u.getUsername());
    }
}
