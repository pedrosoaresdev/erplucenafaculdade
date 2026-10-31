package br.com.lucena.erp.it;

import br.com.lucena.erp.setup.SetupService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Instalação inicial pelo navegador: e-mail do dono + código de uso único → Google Authenticator → usuário e senha. */
@TestPropertySource(properties = {
        "spring.datasource.url=${ERP_TEST_SETUP_DB_URL:jdbc:postgresql://localhost:5432/erp_setup_test}",
        "erp.bootstrap.master-username=",
        "erp.setup.owner-email=Dono@Lucena.edu.br"})
class SetupIT extends IntegrationTestBase {
    @Autowired
    SetupService setup;

    @Test
    void ownerCreatesFirstMasterWithCodeAuthenticatorAndOwnCredentials() throws Exception {
        assertThat(body(mvc.perform(get("/api/setup/status")).andReturn()).get("setupRequired").asBoolean()).isTrue();
        String code = setup.issueCode();

        // e-mail errado ou código errado: mensagem genérica; 5 erros bloqueiam até gerar novo código
        assertThat(postJson("/api/setup/start", Map.of("email", "outro@x.com", "setupCode", code)).getResponse().getStatus()).isEqualTo(401);
        for (int i = 0; i < 4; i++) {
            assertThat(postJson("/api/setup/start", Map.of("email", "dono@lucena.edu.br", "setupCode", "AAAA-BBBB-CCCC")).getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult locked = postJson("/api/setup/start", Map.of("email", "dono@lucena.edu.br", "setupCode", code));
        assertThat(locked.getResponse().getStatus()).isEqualTo(423);
        code = setup.issueCode(); // reinício do servidor gera novo código

        // e-mail sem diferenciar maiúsculas; código sem hífen e em minúsculas
        MvcResult started = postJson("/api/setup/start", Map.of("email", "DONO@lucena.edu.br", "setupCode", code.replace("-", "").toLowerCase()));
        assertThat(started.getResponse().getStatus()).isEqualTo(200);
        JsonNode enr = body(started);
        String token = enr.get("setupToken").asText();
        assertThat(enr.get("qrCodeDataUrl").asText()).startsWith("data:image/png;base64,");
        // gerar novo segredo antes de validar
        enr = body(postJson("/api/setup/totp/regenerate", Map.of("setupToken", token)));
        String secret = enr.get("secret").asText().replace(" ", "");

        Map<String, Object> create = Map.of("setupToken", token, "username", "pedro", "fullName", "Pedro Lucena",
                "password", "Plataforma#Lucena2026", "confirmPassword", "Plataforma#Lucena2026");
        assertThat(body(postJson("/api/setup/complete", create)).get("code").asText()).isEqualTo("TOTP_NOT_VERIFIED");
        assertThat(postJson("/api/setup/totp/verify", Map.of("setupToken", token, "code", "000000")).getResponse().getStatus()).isEqualTo(400);
        clock.advance(Duration.ofSeconds(30));
        assertThat(postJson("/api/setup/totp/verify", Map.of("setupToken", token, "code", totp.codeAt(secret, totp.currentStep()))).getResponse().getStatus()).isEqualTo(204);

        // política de senha aplicada
        assertThat(body(postJson("/api/setup/complete", Map.of("setupToken", token, "username", "pedro", "fullName", "Pedro Lucena",
                "password", "12345678", "confirmPassword", "12345678"))).get("code").asText()).isEqualTo("PASSWORD_TOO_COMMON");
        MvcResult done = postJson("/api/setup/complete", create);
        assertThat(done.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(done).get("recoveryCodes")).hasSize(10);
        assertThat(done.getResponse().getCookie("erp_rt").isHttpOnly()).isTrue();
        Session first = new Session(body(done).get("accessToken").asText(), done.getResponse().getCookie("erp_rt"), body(done));
        JsonNode me = body(call(first, get("/api/me"), null));
        assertThat(me.get("platformAdmin").asBoolean()).isTrue();
        assertThat(me.get("email").asText()).isEqualTo("dono@lucena.edu.br");
        assertThat(me.get("mfaEnabled").asBoolean()).isTrue();

        // instalação concluída: rotas desativadas para sempre
        assertThat(body(mvc.perform(get("/api/setup/status")).andReturn()).get("setupRequired").asBoolean()).isFalse();
        assertThat(postJson("/api/setup/start", Map.of("email", "dono@lucena.edu.br", "setupCode", code)).getResponse().getStatus()).isEqualTo(404);
        assertThat(setup.issueCode()).isNull();

        // próximos acessos: usuário + senha + código do mesmo autenticador
        AUTHENTICATORS.put(key(null, "pedro"), secret);
        Session again = login(null, "pedro", "Plataforma#Lucena2026");
        assertThat(body(call(again, get("/api/platform/dashboard"), null)).get("users").asInt()).isEqualTo(1);
    }
}
