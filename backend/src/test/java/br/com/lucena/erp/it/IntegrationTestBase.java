package br.com.lucena.erp.it;

import br.com.lucena.erp.security.TotpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Testes de integração contra PostgreSQL real (banco erp_test). O schema é recriado pelo Flyway
 * a cada contexto Spring. Inclui helpers para o fluxo completo de login com TOTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.TestConfig.class)
public abstract class IntegrationTestBase {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        FlywayMigrationStrategy cleanMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    public static final String MASTER_PASSWORD = "Plataforma#Teste2026";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected TotpService totp;

    /** Segredos TOTP conhecidos pelo "celular" de cada usuário de teste (chave: faculdade|usuário). */
    protected static final Map<String, String> AUTHENTICATORS = new HashMap<>();

    public record Session(String accessToken, Cookie refreshCookie, JsonNode body) {
        public MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
            return b.header("Authorization", "Bearer " + accessToken);
        }
    }

    protected JsonNode body(MvcResult r) throws Exception {
        String s = r.getResponse().getContentAsString();
        return s.isEmpty() ? json.nullNode() : json.readTree(s);
    }

    /** POST anônimo com corpo JSON. */
    protected MvcResult postJson(String url, Object payload) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andReturn();
    }

    protected MvcResult call(Session s, MockHttpServletRequestBuilder b, Object payload) throws Exception {
        if (payload != null) b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        return mvc.perform(s.auth(b)).andReturn();
    }

    /** Código atual do autenticador; avança o relógio 30 s antes para não repetir passo (anti-replay). */
    protected String nextCode(String key) {
        clock.advance(Duration.ofSeconds(30));
        return totp.codeAt(AUTHENTICATORS.get(key), totp.currentStep());
    }

    protected static String key(String faculty, String username) {
        return (faculty == null ? "" : faculty) + "|" + username;
    }

    protected MvcResult loginStep1(String faculty, String username, String password) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("faculty", faculty);
        req.put("identifier", username);
        req.put("password", password);
        return postJson("/api/auth/login", req);
    }

    /** Login completo: configura o autenticador no primeiro acesso ou informa o código nos seguintes. */
    protected Session login(String faculty, String username, String password) throws Exception {
        MvcResult r1 = loginStep1(faculty, username, password);
        if (r1.getResponse().getStatus() != 200) {
            throw new AssertionError("login falhou: " + r1.getResponse().getContentAsString());
        }
        JsonNode step = body(r1);
        String challenge = step.get("challengeToken").asText();
        MvcResult r2;
        if ("MFA_SETUP_REQUIRED".equals(step.get("nextStep").asText())) {
            JsonNode setup = body(postJson("/api/auth/mfa/setup", Map.of("challengeToken", challenge)));
            AUTHENTICATORS.put(key(faculty, username), setup.get("secret").asText().replace(" ", ""));
            r2 = postJson("/api/auth/mfa/activate", Map.of("challengeToken", challenge, "code", nextCode(key(faculty, username))));
        } else {
            r2 = postJson("/api/auth/mfa/verify", Map.of("challengeToken", challenge, "code", nextCode(key(faculty, username))));
        }
        if (r2.getResponse().getStatus() != 200) {
            throw new AssertionError("MFA falhou: " + r2.getResponse().getContentAsString());
        }
        JsonNode b = body(r2);
        return new Session(b.get("accessToken").asText(), r2.getResponse().getCookie("erp_rt"), b);
    }

    protected Session master() throws Exception {
        return login(null, "master", MASTER_PASSWORD);
    }

    /** Troca de senha obrigatória (step-up com senha atual + TOTP). Retorna nova sessão. */
    protected Session changePassword(Session s, String faculty, String username, String current, String next) throws Exception {
        MvcResult up = call(s, post("/api/me/step-up"), Map.of("currentPassword", current,
                "totpCode", nextCode(key(faculty, username)), "purpose", "PASSWORD_CHANGE"));
        if (up.getResponse().getStatus() != 200) throw new AssertionError(up.getResponse().getContentAsString());
        MvcResult ch = call(s, post("/api/me/password"), Map.of("stepUpToken", body(up).get("stepUpToken").asText(),
                "newPassword", next, "confirmPassword", next));
        if (ch.getResponse().getStatus() != 204) throw new AssertionError(ch.getResponse().getContentAsString());
        return login(faculty, username, next);
    }

    private static int cnpjSeq = 0;

    /** CNPJ válido gerado a partir de uma base sequencial. */
    protected static synchronized String nextCnpj() {
        String base = String.format("%08d0001", 10_000_000 + (++cnpjSeq) * 7919 + (int) (System.nanoTime() % 1000));
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        String b = base + dv(base, w1);
        return b + dv(b, w2);
    }

    private static int dv(String s, int[] w) {
        int sum = 0;
        for (int i = 0; i < w.length; i++) sum += (s.charAt(i) - '0') * w[i];
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    /** Cria faculdade com Diretor Geral inicial; retorna {id, tempPassword}. */
    protected JsonNode createFaculty(Session master, String slug, String adminUsername) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("slug", slug);
        req.put("name", "Faculdade " + slug);
        req.put("legalName", "Faculdade " + slug + " LTDA");
        req.put("cnpj", nextCnpj());
        req.put("email", "contato@" + slug + ".edu.br");
        req.put("initialAdmin", Map.of("username", adminUsername, "fullName", "Diretor " + slug, "email", adminUsername + "@" + slug + ".edu.br"));
        MvcResult r = call(master, post("/api/platform/faculties"), req);
        if (r.getResponse().getStatus() != 201) throw new AssertionError(r.getResponse().getContentAsString());
        return body(r);
    }
}
