package br.com.lucena.erp.auth;

import br.com.lucena.erp.auth.AuthDtos.*;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.config.ErpProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    public static final String REFRESH_COOKIE = "erp_rt";
    public static final String CLIENT_HEADER = "X-ERP-Client";

    private final AuthService auth;
    private final SessionService sessions;
    private final ErpProperties props;

    @Operation(summary = "Etapa 1: matrícula/usuário + senha")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, ClientInfo.from(http));
    }

    @Operation(summary = "Primeiro acesso: gera QR Code e chave secreta (ou novo segredo enquanto não ativado)")
    @PostMapping("/mfa/setup")
    public MfaSetupResponse setup(@Valid @RequestBody ChallengeRequest req) {
        return auth.setupMfa(req.challengeToken());
    }

    @Operation(summary = "Primeiro acesso: valida o código do Google Authenticator e ativa o 2º fator")
    @PostMapping("/mfa/activate")
    public TokenResponse activate(@Valid @RequestBody CodeRequest req, HttpServletRequest http, HttpServletResponse res) {
        return withCookie(auth.activateMfa(req, ClientInfo.from(http)), res);
    }

    @Operation(summary = "Etapa 2: código TOTP de 6 dígitos")
    @PostMapping("/mfa/verify")
    public TokenResponse verify(@Valid @RequestBody CodeRequest req, HttpServletRequest http, HttpServletResponse res) {
        return withCookie(auth.verifyMfa(req, ClientInfo.from(http)), res);
    }

    @Operation(summary = "Etapa 2 alternativa: código de recuperação de uso único")
    @PostMapping("/mfa/recovery")
    public TokenResponse recovery(@Valid @RequestBody RecoveryLoginRequest req, HttpServletRequest http, HttpServletResponse res) {
        return withCookie(auth.recoveryLogin(req, ClientInfo.from(http)), res);
    }

    @Operation(summary = "Renova o access token usando o cookie HttpOnly de sessão")
    @PostMapping("/refresh")
    public TokenResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh,
                                 @RequestHeader(name = CLIENT_HEADER, required = false) String clientHeader,
                                 HttpServletRequest http, HttpServletResponse res) {
        if (clientHeader == null) {
            throw ApiException.forbidden("Cabeçalho de cliente ausente");
        }
        SessionService.IssuedTokens t = sessions.refresh(refresh, ClientInfo.from(http));
        setCookie(res, t.refreshToken(), props.security().refreshTokenTtl());
        return new TokenResponse(t.accessToken(), "Bearer", t.expiresIn(), false, null, null);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh,
                                       HttpServletResponse res) {
        sessions.revokeByRefreshToken(refresh);
        setCookie(res, "", Duration.ZERO);
        return ResponseEntity.noContent().build();
    }

    private TokenResponse withCookie(AuthService.Issued issued, HttpServletResponse res) {
        setCookie(res, issued.tokens().refreshToken(), props.security().refreshTokenTtl());
        return issued.body();
    }

    private void setCookie(HttpServletResponse res, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.security().cookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
