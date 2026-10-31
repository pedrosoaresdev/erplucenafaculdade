package br.com.lucena.erp.auth;

import br.com.lucena.erp.config.ErpProperties;
import br.com.lucena.erp.crypto.Tokens;
import br.com.lucena.erp.iam.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Códigos de recuperação: exibidos uma única vez, armazenados como SHA-256 e de uso único. */
@Service
@RequiredArgsConstructor
public class RecoveryCodeService {
    private final RecoveryCodeRepository repo;
    private final ErpProperties props;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> regenerate(User user) {
        repo.deleteAllForUser(user.getId());
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < props.security().recoveryCodeCount(); i++) {
            String code = Tokens.recoveryCode();
            RecoveryCode rc = new RecoveryCode();
            rc.setUserId(user.getId());
            rc.setCodeHash(Tokens.sha256Hex(code));
            repo.save(rc);
            codes.add(code);
        }
        return codes;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consume(User user, String input) {
        String hash = Tokens.sha256Hex(Tokens.normalizeRecoveryCode(input));
        return repo.findByUserIdAndCodeHashAndUsedAtIsNull(user.getId(), hash)
                .map(rc -> repo.consume(rc.getId(), clock.instant()) == 1)
                .orElse(false);
    }

    public long remaining(User user) {
        return repo.countByUserIdAndUsedAtIsNull(user.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAll(User user) {
        repo.deleteAllForUser(user.getId());
    }
}
