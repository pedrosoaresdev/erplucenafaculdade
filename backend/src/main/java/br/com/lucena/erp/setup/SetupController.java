package br.com.lucena.erp.setup;

import br.com.lucena.erp.auth.AuthController;
import br.com.lucena.erp.auth.AuthDtos;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.config.ErpProperties;
import br.com.lucena.erp.setup.SetupService.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Instalação inicial da plataforma (somente enquanto não existir administrador Master). */
@Tag(name = "Instalação inicial")
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {
    private final SetupService setup;
    private final ErpProperties props;
    private final br.com.lucena.erp.audit.AuditService audit;

    @GetMapping("/status")
    public Status status() {
        return setup.status();
    }

    @PostMapping("/start")
    public Enrollment start(@Valid @RequestBody StartRequest req, HttpServletRequest http) {
        return setup.start(req, ClientInfo.from(http));
    }

    @PostMapping("/totp/regenerate")
    public Enrollment regenerate(@Valid @RequestBody TokenRequest req) {
        return setup.regenerate(req);
    }

    @PostMapping("/totp/verify")
    public ResponseEntity<Void> verify(@Valid @RequestBody CodeRequest req) {
        setup.verifyTotp(req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/complete")
    public AuthDtos.TokenResponse complete(@Valid @RequestBody CompleteRequest req, HttpServletRequest http, HttpServletResponse res) {
        ClientInfo client = ClientInfo.from(http);
        Completed c = setup.complete(req, client);
        // log de acesso em transação própria, depois que o usuário já foi gravado
        audit.access(c.userId(), null, req.username(), "LOGIN_MFA_SETUP", true, "instalação inicial", client);
        AuthController.writeRefreshCookie(res, c.tokens().refreshToken(), props.security().refreshTokenTtl(), props.security().cookieSecure());
        return c.body();
    }
}
