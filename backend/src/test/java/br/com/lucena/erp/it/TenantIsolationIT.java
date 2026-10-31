package br.com.lucena.erp.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class TenantIsolationIT extends IntegrationTestBase {
    static final String PWD = "Gestao#Segura2026";

    @Autowired
    JdbcTemplate jdbc;

    record Fac(String slug, String id, Session admin) {
    }

    private Fac readyFaculty() throws Exception {
        String slug = "iso-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode f = createFaculty(master(), slug, "diretor");
        Session s = login(slug, "diretor", f.get("initialAdminTemporaryPassword").asText());
        s = changePassword(s, slug, "diretor", f.get("initialAdminTemporaryPassword").asText(), PWD);
        return new Fac(slug, f.get("faculty").get("id").asText(), s);
    }

    private String roleId(Session s, String code) throws Exception {
        for (JsonNode r : body(call(s, get("/api/admin/roles"), null))) {
            if (r.get("code").asText().equals(code)) return r.get("id").asText();
        }
        throw new AssertionError("cargo " + code);
    }

    @Test
    void usersOnlySeeTheirOwnFaculty() throws Exception {
        Fac a = readyFaculty();
        Fac b = readyFaculty();
        // mesmo username em faculdades diferentes é permitido e o login é escopado pela faculdade
        JsonNode usersA = body(call(a.admin(), get("/api/admin/users"), null));
        assertThat(usersA.get("content")).allSatisfy(u -> assertThat(u.get("facultyId").asText()).isEqualTo(a.id()));

        assertThat(call(a.admin(), get("/api/admin/users").param("facultyId", b.id()), null).getResponse().getStatus()).isEqualTo(403);
        String userOfB = body(call(b.admin(), get("/api/admin/users"), null)).get("content").get(0).get("id").asText();
        assertThat(call(a.admin(), get("/api/admin/users/" + userOfB), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(a.admin(), post("/api/admin/users/" + userOfB + "/reset-password"), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(a.admin(), put("/api/admin/users/" + userOfB + "/roles"), Map.of("roleIds", List.of(roleId(a.admin(), "ALUNO"))))
                .getResponse().getStatus()).isEqualTo(404);
        // cargo de outra faculdade não pode ser atribuído
        assertThat(call(a.admin(), post("/api/admin/users"), Map.of("username", "x.user", "fullName", "X", "email", "x@x.com",
                "roleIds", List.of(roleId(b.admin(), "ALUNO")))).getResponse().getStatus()).isEqualTo(400);
        // Painel Master inacessível
        assertThat(call(a.admin(), get("/api/platform/dashboard"), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(a.admin(), get("/api/platform/faculties"), null).getResponse().getStatus()).isEqualTo(403);
        // auditoria só da própria faculdade
        JsonNode audit = body(call(a.admin(), get("/api/admin/audit"), null));
        assertThat(audit.get("content")).allSatisfy(e -> assertThat(e.get("facultyId").asText()).isEqualTo(a.id()));
        assertThat(call(a.admin(), get("/api/admin/audit").param("facultyId", b.id()), null).getResponse().getStatus()).isEqualTo(403);

        Session m = master();
        assertThat(call(m, get("/api/admin/users"), null).getResponse().getStatus()).isEqualTo(400);
        assertThat(body(call(m, get("/api/admin/users").param("facultyId", b.id()), null)).get("totalElements").asLong()).isEqualTo(1);
        call(m, get("/api/admin/roles").param("facultyId", b.id()), null);
        // acesso de suporte do Master registrado uma vez (janela de 30 min) e visível na auditoria da própria faculdade
        JsonNode supportAudit = body(call(b.admin(), get("/api/admin/audit").param("action", "PLATFORM_SUPPORT_ACCESS"), null));
        assertThat(supportAudit.get("totalElements").asLong()).isEqualTo(1);
        assertThat(supportAudit.get("content").get(0).get("actorName").asText()).isEqualTo("master");
        assertThat(body(call(a.admin(), get("/api/admin/audit").param("action", "PLATFORM_SUPPORT_ACCESS"), null)).get("totalElements").asLong()).isZero();
        JsonNode dash = body(call(m, get("/api/platform/dashboard"), null));
        assertThat(dash.get("faculties").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void privilegeEscalationIsBlocked() throws Exception {
        Fac a = readyFaculty();
        MvcResult created = call(a.admin(), post("/api/admin/users"), Map.of("username", "suporte", "fullName", "Equipe Suporte",
                "email", "suporte@x.edu.br", "roleIds", List.of(roleId(a.admin(), "SUPORTE_TI"))));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String temp = body(created).get("temporaryPassword").asText();
        String supportId = body(created).get("user").get("id").asText();
        Session sup = login(a.slug(), "suporte", temp);
        sup = changePassword(sup, a.slug(), "suporte", temp, PWD);

        // Suporte/TI não concede cargos que excedem suas permissões
        assertThat(call(sup, post("/api/admin/users"), Map.of("username", "novo.diretor", "fullName", "N", "email", "n@x.com",
                "roleIds", List.of(roleId(sup, "DIRETOR_GERAL")))).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(sup, put("/api/admin/users/" + supportId + "/roles"), Map.of("roleIds", List.of(roleId(sup, "DIRETOR_GERAL"))))
                .getResponse().getStatus()).isEqualTo(403);
        // nem cria cargo com permissões que não possui
        assertThat(call(sup, post("/api/admin/roles"), Map.of("code", "SUPER", "name", "Super", "permissions", List.of("FINANCE_MANAGE")))
                .getResponse().getStatus()).isEqualTo(403);
        // Suporte da faculdade não é administrador da plataforma
        assertThat(call(sup, get("/api/platform/faculties"), null).getResponse().getStatus()).isEqualTo(403);
        // pode criar usuário com o próprio cargo
        assertThat(call(sup, post("/api/admin/users"), Map.of("username", "suporte2", "fullName", "Suporte 2", "email", "s2@x.com",
                "roleIds", List.of(roleId(sup, "SUPORTE_TI")))).getResponse().getStatus()).isEqualTo(201);

        // Diretor cria cargo personalizado; permissão exclusiva da plataforma é rejeitada
        assertThat(call(a.admin(), post("/api/admin/roles"), Map.of("code", "TESOURARIA", "name", "Tesouraria",
                "permissions", List.of("FINANCE_VIEW", "FINANCE_REPORTS")))).extracting(r -> r.getResponse().getStatus()).isEqualTo(201);
        assertThat(call(a.admin(), post("/api/admin/roles"), Map.of("code", "HACK", "name", "Hack",
                "permissions", List.of("PLATFORM_ADMIN")))).extracting(r -> r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void suspendingFacultyRevokesAccessAndReactivationRestores() throws Exception {
        Fac a = readyFaculty();
        Session m = master();
        assertThat(call(m, post("/api/platform/faculties/" + a.id() + "/status"), Map.of("action", "SUSPEND"))
                .getResponse().getStatus()).isEqualTo(400); // motivo obrigatório
        assertThat(call(m, post("/api/platform/faculties/" + a.id() + "/status"), Map.of("action", "SUSPEND", "reason", "Inadimplência contratual"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(call(a.admin(), get("/api/me"), null).getResponse().getStatus()).isEqualTo(401);
        MvcResult blocked = loginStep1(a.slug(), "diretor", PWD);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("FACULTY_SUSPENDED");

        call(m, post("/api/platform/faculties/" + a.id() + "/status"), Map.of("action", "REACTIVATE"));
        assertThat(login(a.slug(), "diretor", PWD).accessToken()).isNotBlank();
        // com dados, exclusão física é recusada (arquivamento)
        assertThat(call(m, delete("/api/platform/faculties/" + a.id()), null).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void adminMfaResetIsAuditedAndForcesReconfiguration() throws Exception {
        Fac a = readyFaculty();
        MvcResult created = call(a.admin(), post("/api/admin/users"), Map.of("username", "prof.ana", "fullName", "Ana Professora",
                "email", "ana@x.edu.br", "roleIds", List.of(roleId(a.admin(), "PROFESSOR"))));
        String id = body(created).get("user").get("id").asText();
        String temp = body(created).get("temporaryPassword").asText();
        login(a.slug(), "prof.ana", temp);

        assertThat(call(a.admin(), post("/api/admin/users/" + id + "/reset-mfa"), Map.of("reason", "curto", "identityVerification", "x"))
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(call(a.admin(), post("/api/admin/users/" + id + "/reset-mfa"),
                Map.of("reason", "Perdeu o celular com o autenticador", "identityVerification", "Documento com foto conferido presencialmente"))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(body(loginStep1(a.slug(), "prof.ana", temp)).get("nextStep").asText()).isEqualTo("MFA_SETUP_REQUIRED");
        Integer audited = jdbc.queryForObject("select count(*) from audit_logs where action = 'MFA_RESET_BY_ADMIN' and entity_id = ?", Integer.class, id);
        assertThat(audited).isEqualTo(1);

        // Trilha de auditoria é somente-inserção
        assertThatThrownBy(() -> jdbc.update("update audit_logs set action = 'X' where entity_id = ?", id)).hasMessageContaining("somente-inser");
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where entity_id = ?", id)).hasMessageContaining("somente-inser");
    }

    @Test
    void facultyValidationAndPublicBranding() throws Exception {
        Session m = master();
        MvcResult bad = call(m, post("/api/platform/faculties"), Map.of("slug", "x-y", "name", "X", "legalName", "X", "cnpj", "11222333000182", "email", "a@b.com"));
        assertThat(body(bad).get("code").asText()).isEqualTo("INVALID_CNPJ");
        Fac a = readyFaculty();
        JsonNode pub = body(mvc.perform(get("/api/public/faculties/" + a.slug())).andReturn());
        assertThat(pub.get("name").asText()).contains(a.slug());
        assertThat(pub.has("cnpj")).isFalse();
        assertThat(mvc.perform(get("/api/admin/users")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
