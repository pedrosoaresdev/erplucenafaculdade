package br.com.lucena.erp.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AuthFlowIT extends IntegrationTestBase {
    static final String NEW_PWD = "Novo#Acesso2026x";

    @Autowired
    JdbcTemplate jdbc;

    /** Faculdade nova com Diretor (senha temporária). Retorna slug, usuário e senha temporária. */
    private String[] freshFaculty() throws Exception {
        String slug = "fac-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode f = createFaculty(master(), slug, "diretor");
        return new String[]{slug, "diretor", f.get("initialAdminTemporaryPassword").asText()};
    }

    /** Usuário pronto: MFA configurado e senha definitiva. */
    private String[] readyUser() throws Exception {
        String[] u = freshFaculty();
        Session s = login(u[0], u[1], u[2]);
        changePassword(s, u[0], u[1], u[2], NEW_PWD);
        return new String[]{u[0], u[1], NEW_PWD};
    }

    @Test
    void firstAccessRequiresAuthenticatorSetupWithRealCodeValidation() throws Exception {
        String[] u = freshFaculty();
        JsonNode step = body(loginStep1(u[0], u[1], u[2]));
        assertThat(step.get("nextStep").asText()).isEqualTo("MFA_SETUP_REQUIRED");
        String ch = step.get("challengeToken").asText();

        // Desafio de configuração não serve para a etapa de verificação comum
        assertThat(postJson("/api/auth/mfa/verify", Map.of("challengeToken", ch, "code", "123456")).getResponse().getStatus()).isEqualTo(401);
        // Validar antes de gerar o QR Code
        assertThat(postJson("/api/auth/mfa/activate", Map.of("challengeToken", ch, "code", "123456")).getResponse().getStatus()).isEqualTo(400);

        JsonNode first = body(postJson("/api/auth/mfa/setup", Map.of("challengeToken", ch)));
        assertThat(first.get("qrCodeDataUrl").asText()).startsWith("data:image/png;base64,");
        assertThat(first.get("otpauthUri").asText()).startsWith("otpauth://totp/");
        String firstSecret = first.get("secret").asText().replace(" ", "");

        // "Gerar novo segredo": o anterior deixa de valer
        JsonNode second = body(postJson("/api/auth/mfa/setup", Map.of("challengeToken", ch)));
        String secret = second.get("secret").asText().replace(" ", "");
        assertThat(secret).isNotEqualTo(firstSecret);

        clock.advance(Duration.ofSeconds(30));
        MvcResult oldSecret = postJson("/api/auth/mfa/activate", Map.of("challengeToken", ch, "code", totp.codeAt(firstSecret, totp.currentStep())));
        assertThat(oldSecret.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(oldSecret).get("code").asText()).isEqualTo("INVALID_CODE");

        MvcResult ok = postJson("/api/auth/mfa/activate", Map.of("challengeToken", ch, "code", totp.codeAt(secret, totp.currentStep())));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        JsonNode tokens = body(ok);
        assertThat(tokens.get("recoveryCodes")).hasSize(10);
        assertThat(tokens.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(ok.getResponse().getCookie("erp_rt").isHttpOnly()).isTrue();

        // Segredo cifrado e códigos de recuperação somente em hash
        String stored = jdbc.queryForObject("""
                select s.secret_ciphertext from user_authenticator_secrets s join users u on u.id = s.user_id
                join faculties f on f.id = u.faculty_id where f.slug = ? and s.status = 'ACTIVE'""", String.class, u[0]);
        assertThat(stored).startsWith("v1:").doesNotContain(secret);
        String code0 = tokens.get("recoveryCodes").get(0).asText();
        Integer plain = jdbc.queryForObject("select count(*) from recovery_codes where code_hash = ?", Integer.class, code0);
        assertThat(plain).isZero();
        Integer audits = jdbc.queryForObject("select count(*) from audit_logs where action = 'MFA_ACTIVATED' and actor_name = ? and faculty_id = (select id from faculties where slug = ?)",
                Integer.class, u[1], u[0]);
        assertThat(audits).isEqualTo(1);

        // Desafio já consumido não pode ser reutilizado
        assertThat(postJson("/api/auth/mfa/setup", Map.of("challengeToken", ch)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void concurrentSetupRequestsAreSerialized() throws Exception {
        String[] u = freshFaculty();
        String ch = body(loginStep1(u[0], u[1], u[2])).get("challengeToken").asText();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> postJson("/api/auth/mfa/setup", Map.of("challengeToken", ch)).getResponse().getStatus()));
            }
            for (var f : futures) assertThat(f.get()).isEqualTo(200);
        } finally {
            pool.shutdown();
        }
        Integer pending = jdbc.queryForObject("""
                select count(*) from user_authenticator_secrets s join users u on u.id = s.user_id
                join faculties f on f.id = u.faculty_id where f.slug = ? and s.status = 'PENDING'""", Integer.class, u[0]);
        assertThat(pending).isEqualTo(1);
    }

    @Test
    void subsequentLoginsAskForCodeWithoutNewQrAndRejectReplay() throws Exception {
        String[] u = readyUser();
        JsonNode step = body(loginStep1(u[0], u[1], u[2]));
        assertThat(step.get("nextStep").asText()).isEqualTo("MFA_REQUIRED");
        String ch = step.get("challengeToken").asText();
        assertThat(postJson("/api/auth/mfa/setup", Map.of("challengeToken", ch)).getResponse().getStatus()).isEqualTo(401);

        String code = nextCode(key(u[0], u[1]));
        assertThat(postJson("/api/auth/mfa/verify", Map.of("challengeToken", ch, "code", code)).getResponse().getStatus()).isEqualTo(200);

        // Mesmo código, novo login dentro da mesma janela de 30 s: rejeitado (anti-replay)
        String ch2 = body(loginStep1(u[0], u[1], u[2])).get("challengeToken").asText();
        MvcResult replay = postJson("/api/auth/mfa/verify", Map.of("challengeToken", ch2, "code", code));
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(replay).get("code").asText()).isEqualTo("INVALID_CODE");
    }

    @Test
    void temporaryPasswordBlocksModulesUntilChanged() throws Exception {
        String[] u = freshFaculty();
        Session s = login(u[0], u[1], u[2]);
        assertThat(call(s, get("/api/admin/users"), null).getResponse().getStatus()).isEqualTo(403);
        JsonNode me = body(call(s, get("/api/me"), null));
        assertThat(me.get("mustChangePassword").asBoolean()).isTrue();
        Session s2 = changePassword(s, u[0], u[1], u[2], NEW_PWD);
        assertThat(call(s2, get("/api/admin/users"), null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void passwordChangeRequiresPasswordAndTotpAndRevokesSessions() throws Exception {
        String[] u = readyUser();
        Session s = login(u[0], u[1], u[2]);
        Session other = login(u[0], u[1], u[2]);

        MvcResult badTotp = call(s, post("/api/me/step-up"), Map.of("currentPassword", u[2], "totpCode", "000000", "purpose", "PASSWORD_CHANGE"));
        assertThat(badTotp.getResponse().getStatus()).isEqualTo(400);
        MvcResult badPwd = call(s, post("/api/me/step-up"), Map.of("currentPassword", "errada!!", "totpCode", nextCode(key(u[0], u[1])), "purpose", "PASSWORD_CHANGE"));
        assertThat(badPwd.getResponse().getStatus()).isEqualTo(400);

        // Sem step-up válido
        assertThat(call(s, post("/api/me/password"), Map.of("stepUpToken", "invalido", "newPassword", "Outra#Senha2026", "confirmPassword", "Outra#Senha2026"))
                .getResponse().getStatus()).isEqualTo(403);

        String token = stepUp(s, u, "PASSWORD_CHANGE");
        assertThat(code(call(s, post("/api/me/password"), Map.of("stepUpToken", token, "newPassword", "Outra#Senha2026", "confirmPassword", "Outra#Senha2027"))))
                .isEqualTo("PASSWORD_MISMATCH");
        token = stepUp(s, u, "PASSWORD_CHANGE");
        assertThat(code(call(s, post("/api/me/password"), Map.of("stepUpToken", token, "newPassword", "curta1!", "confirmPassword", "curta1!"))))
                .isEqualTo("PASSWORD_TOO_SHORT");
        token = stepUp(s, u, "PASSWORD_CHANGE");
        assertThat(code(call(s, post("/api/me/password"), Map.of("stepUpToken", token, "newPassword", u[2], "confirmPassword", u[2]))))
                .isEqualTo("PASSWORD_SAME_AS_CURRENT");
        // Token de step-up de outro propósito não serve
        String wrongPurpose = stepUp(s, u, "RECOVERY_CODES");
        assertThat(call(s, post("/api/me/password"), Map.of("stepUpToken", wrongPurpose, "newPassword", "Outra#Senha2026", "confirmPassword", "Outra#Senha2026"))
                .getResponse().getStatus()).isEqualTo(403);

        token = stepUp(s, u, "PASSWORD_CHANGE");
        assertThat(call(s, post("/api/me/password"), Map.of("stepUpToken", token, "newPassword", "Outra#Senha2026", "confirmPassword", "Outra#Senha2026"))
                .getResponse().getStatus()).isEqualTo(204);

        // Todas as sessões revogadas, senha antiga inválida, nova válida
        assertThat(call(s, get("/api/me"), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(other, get("/api/me"), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(loginStep1(u[0], u[1], u[2]).getResponse().getStatus()).isEqualTo(401);
        Session fresh = login(u[0], u[1], "Outra#Senha2026");
        JsonNode notifications = body(call(fresh, get("/api/me/notifications"), null));
        assertThat(notifications.get("content").toString()).contains("Senha alterada");
        String hash = jdbc.queryForObject("select password_hash from users u join faculties f on f.id = u.faculty_id where f.slug = ? and u.username = ?",
                String.class, u[0], u[1]);
        assertThat(hash).startsWith("$argon2id$");
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        String[] u = readyUser();
        for (int i = 0; i < 4; i++) {
            assertThat(loginStep1(u[0], u[1], "errada#123").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(loginStep1(u[0], u[1], "errada#123").getResponse().getStatus()).isEqualTo(423);
        assertThat(loginStep1(u[0], u[1], u[2]).getResponse().getStatus()).isEqualTo(423);
        clock.advance(Duration.ofMinutes(16));
        assertThat(loginStep1(u[0], u[1], u[2]).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unknownUserGetsSameGenericError() throws Exception {
        MvcResult r = loginStep1("nao-existe", "fulano", "qualquer#123");
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(r).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void wrongCodesExhaustChallenge() throws Exception {
        String[] u = readyUser();
        String ch = body(loginStep1(u[0], u[1], u[2])).get("challengeToken").asText();
        for (int i = 0; i < 4; i++) {
            assertThat(postJson("/api/auth/mfa/verify", Map.of("challengeToken", ch, "code", "000000")).getResponse().getStatus()).isEqualTo(401);
        }
        int fifth = postJson("/api/auth/mfa/verify", Map.of("challengeToken", ch, "code", "000000")).getResponse().getStatus();
        assertThat(fifth).isIn(401, 423);
        assertThat(postJson("/api/auth/mfa/verify", Map.of("challengeToken", ch, "code", nextCode(key(u[0], u[1])))).getResponse().getStatus())
                .isIn(401, 423);
    }

    @Test
    void recoveryCodeWorksOnlyOnce() throws Exception {
        String[] u = readyUser();
        Session s = login(u[0], u[1], u[2]);
        JsonNode codes = body(call(s, post("/api/me/mfa/recovery-codes"), Map.of("stepUpToken", stepUp(s, u, "RECOVERY_CODES"))));
        String code = codes.get("codes").get(3).asText();

        String ch = body(loginStep1(u[0], u[1], u[2])).get("challengeToken").asText();
        MvcResult ok = postJson("/api/auth/mfa/recovery", Map.of("challengeToken", ch, "recoveryCode", code.toLowerCase()));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(ok).get("remainingRecoveryCodes").asLong()).isEqualTo(9);

        String ch2 = body(loginStep1(u[0], u[1], u[2])).get("challengeToken").asText();
        assertThat(postJson("/api/auth/mfa/recovery", Map.of("challengeToken", ch2, "recoveryCode", code)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshRotationAndLogout() throws Exception {
        String[] u = readyUser();
        Session s = login(u[0], u[1], u[2]);
        assertThat(mvc.perform(post("/api/auth/refresh").cookie(s.refreshCookie())).andReturn().getResponse().getStatus()).isEqualTo(403);
        MvcResult r = mvc.perform(post("/api/auth/refresh").cookie(s.refreshCookie()).header("X-ERP-Client", "web")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        var rotated = r.getResponse().getCookie("erp_rt");
        assertThat(rotated.getValue()).isNotEqualTo(s.refreshCookie().getValue());
        // Refresh antigo já rotacionado não funciona mais
        assertThat(mvc.perform(post("/api/auth/refresh").cookie(s.refreshCookie()).header("X-ERP-Client", "web")).andReturn().getResponse().getStatus()).isEqualTo(401);

        mvc.perform(post("/api/auth/logout").cookie(rotated));
        assertThat(mvc.perform(post("/api/auth/refresh").cookie(rotated).header("X-ERP-Client", "web")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(call(s, get("/api/me"), null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void authenticatorRotationRequiresStepUpAndNewDeviceCode() throws Exception {
        String[] u = readyUser();
        Session s = login(u[0], u[1], u[2]);
        Session other = login(u[0], u[1], u[2]);
        assertThat(call(s, post("/api/me/mfa/rotation/start"), Map.of("stepUpToken", "x")).getResponse().getStatus()).isEqualTo(403);

        JsonNode enrollment = body(call(s, post("/api/me/mfa/rotation/start"), Map.of("stepUpToken", stepUp(s, u, "MFA_ROTATE"))));
        String newSecret = enrollment.get("secret").asText().replace(" ", "");
        assertThat(body(call(s, get("/api/me/mfa"), null)).get("rotationPending").asBoolean()).isTrue();

        // Código do dispositivo antigo não confirma a troca
        assertThat(call(s, post("/api/me/mfa/rotation/confirm"), Map.of("code", nextCode(key(u[0], u[1])))).getResponse().getStatus()).isEqualTo(400);
        clock.advance(Duration.ofSeconds(30));
        MvcResult ok = call(s, post("/api/me/mfa/rotation/confirm"), Map.of("code", totp.codeAt(newSecret, totp.currentStep())));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(ok).get("codes")).hasSize(10);

        // Sessão atual mantida, demais revogadas; dispositivo antigo não autentica mais
        assertThat(call(s, get("/api/me"), null).getResponse().getStatus()).isEqualTo(200);
        assertThat(call(other, get("/api/me"), null).getResponse().getStatus()).isEqualTo(401);
        String ch = body(loginStep1(u[0], u[1], u[2])).get("challengeToken").asText();
        assertThat(postJson("/api/auth/mfa/verify", Map.of("challengeToken", ch, "code", nextCode(key(u[0], u[1])))).getResponse().getStatus()).isEqualTo(401);
        AUTHENTICATORS.put(key(u[0], u[1]), newSecret);
        assertThat(login(u[0], u[1], u[2]).accessToken()).isNotBlank();
    }

    @Test
    void sessionsCanBeListedAndRevoked() throws Exception {
        String[] u = readyUser();
        Session a = login(u[0], u[1], u[2]);
        Session b = login(u[0], u[1], u[2]);
        JsonNode list = body(call(a, get("/api/me/sessions"), null));
        assertThat(list.size()).isGreaterThanOrEqualTo(2);
        assertThat(body(call(a, post("/api/me/sessions/revoke-others"), null)).get("revoked").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(call(b, get("/api/me"), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(a, get("/api/me"), null).getResponse().getStatus()).isEqualTo(200);
        assertThat(body(call(a, get("/api/me/access-history"), null)).get("totalElements").asLong()).isGreaterThan(0);
    }

    private String stepUp(Session s, String[] u, String purpose) throws Exception {
        MvcResult r = call(s, post("/api/me/step-up"), Map.of("currentPassword", u[2], "totpCode", nextCode(key(u[0], u[1])), "purpose", purpose));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return body(r).get("stepUpToken").asText();
    }

    private String code(MvcResult r) throws Exception {
        return body(r).get("code").asText();
    }
}
