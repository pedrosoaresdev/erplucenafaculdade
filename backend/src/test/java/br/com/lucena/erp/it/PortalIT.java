package br.com.lucena.erp.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Fase 5: portal do professor (diário, notas, frequência) e portal do aluno. */
class PortalIT extends IntegrationTestBase {
    static final String PWD = "Gestao#Segura2026";

    @Autowired
    JdbcTemplate jdbc;

    /** Cenário: turma com uma disciplina ofertada ao professor e dois alunos. */
    record Scenario(String slug, Session admin, Session teacher, Session otherTeacher, Session s1, Session s2,
                    String s1Id, String s2Id, String csId, String termId, String otherCsId) {
    }

    private JsonNode ok(Session s, MockHttpServletRequestBuilder b, Object payload) throws Exception {
        MvcResult r = call(s, b, payload);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isBetween(200, 299);
        return body(r);
    }

    private String code(MvcResult r) throws Exception {
        return body(r).get("code").asText();
    }

    private Session firstLogin(String slug, String username, String temp, String finalPwd) throws Exception {
        return changePassword(login(slug, username, temp), slug, username, temp, finalPwd);
    }

    private Scenario scenario() throws Exception {
        String slug = "port-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode f = createFaculty(master(), slug, "diretor");
        String temp = f.get("initialAdminTemporaryPassword").asText();
        Session a = firstLogin(slug, "diretor", temp, PWD);

        String course = ok(a, post("/api/academic/courses"), Map.of("code", "ENG", "name", "Engenharia", "degree", "BACHARELADO",
                "modality", "PRESENCIAL", "durationSemesters", 10, "monthlyFee", 1500, "active", true)).get("id").asText();
        String calc = ok(a, post("/api/academic/subjects"), Map.of("code", "CALC1", "name", "Cálculo I", "workloadHours", 80, "credits", 4, "active", true)).get("id").asText();
        String fis = ok(a, post("/api/academic/subjects"), Map.of("code", "FIS1", "name", "Física I", "workloadHours", 60, "credits", 4, "active", true)).get("id").asText();
        ok(a, post("/api/academic/courses/" + course + "/curriculum"), Map.of("subjectId", calc, "semester", 1, "mandatory", true));
        ok(a, post("/api/academic/courses/" + course + "/curriculum"), Map.of("subjectId", fis, "semester", 1, "mandatory", true));
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        String termId = ok(a, post("/api/academic/terms"), Map.of("code", today.getYear() + ".1", "startDate", today.minusDays(30).toString(),
                "endDate", today.plusDays(60).toString())).get("id").asText();
        ok(a, post("/api/academic/terms/" + termId + "/status"), Map.of("status", "OPEN"));
        String classId = ok(a, post("/api/academic/classes"), Map.of("courseId", course, "termId", termId, "code", "ENG1", "semester", 1,
                "shift", "MANHA", "capacity", 40, "room", "A1", "active", true)).get("id").asText();

        JsonNode t1 = ok(a, post("/api/academic/teachers"), Map.of("fullName", "Prof Titular", "cpf", nextCpf(), "email", "tit@x.edu.br", "status", "ACTIVE", "username", "titular"));
        JsonNode t2 = ok(a, post("/api/academic/teachers"), Map.of("fullName", "Prof Outro", "cpf", nextCpf(), "email", "out@x.edu.br", "status", "ACTIVE", "username", "outro"));
        JsonNode d = ok(a, post("/api/academic/classes/" + classId + "/subjects"), Map.of("subjectId", calc, "teacherId", t1.get("record").get("id").asText(),
                "weekday", 2, "startTime", "08:00", "endTime", "09:40"));
        d = ok(a, post("/api/academic/classes/" + classId + "/subjects"), Map.of("subjectId", fis, "teacherId", t2.get("record").get("id").asText(),
                "weekday", 3, "startTime", "08:00", "endTime", "09:40"));
        String csId = null, otherCs = null;
        for (JsonNode s : d.get("subjects")) {
            if (s.get("subjectCode").asText().equals("CALC1")) csId = s.get("id").asText(); else otherCs = s.get("id").asText();
        }
        JsonNode st1 = ok(a, post("/api/academic/students"), Map.of("fullName", "Aluno Um", "cpf", nextCpf(), "email", "um@a.br", "createAccess", true));
        JsonNode st2 = ok(a, post("/api/academic/students"), Map.of("fullName", "Aluno Dois", "cpf", nextCpf(), "email", "dois@a.br", "createAccess", true));
        String s1Id = st1.get("record").get("id").asText(), s2Id = st2.get("record").get("id").asText();
        ok(a, post("/api/academic/classes/" + classId + "/students"), Map.of("studentId", s1Id));
        ok(a, post("/api/academic/classes/" + classId + "/students"), Map.of("studentId", s2Id));

        Session teacher = firstLogin(slug, "titular", t1.get("temporaryPassword").asText(), "Docente#Forte2026");
        Session other = firstLogin(slug, "outro", t2.get("temporaryPassword").asText(), "Docente#Forte2026");
        Session s1 = firstLogin(slug, st1.get("username").asText(), st1.get("temporaryPassword").asText(), "Estudo#Forte2026");
        Session s2 = firstLogin(slug, st2.get("username").asText(), st2.get("temporaryPassword").asText(), "Estudo#Forte2026");
        return new Scenario(slug, a, teacher, other, s1, s2, s1Id, s2Id, csId, termId, otherCs);
    }

    private Map<String, Object> assessment(String title, String kind, double weight, double max) {
        return Map.of("title", title, "kind", kind, "weight", weight, "maxScore", max);
    }

    private String assessmentId(JsonNode diary, String title) {
        for (JsonNode a : diary.get("assessments")) if (a.get("title").asText().equals(title)) return a.get("id").asText();
        throw new AssertionError(title);
    }

    private JsonNode row(JsonNode diary, String studentId) {
        for (JsonNode r : diary.get("students")) if (r.get("studentId").asText().equals(studentId)) return r;
        throw new AssertionError(studentId);
    }

    @Test
    void teacherDiaryGradesAttendanceAndFinalSituation() throws Exception {
        Scenario sc = scenario();
        Session t = sc.teacher();
        String base = "/api/teacher/class-subjects/" + sc.csId();

        // escopo: só as próprias aulas
        JsonNode mine = ok(t, get("/api/teacher/classes"), null);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("subjectCode").asText()).isEqualTo("CALC1");
        assertThat(call(sc.otherTeacher(), get(base), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(t, get("/api/teacher/class-subjects/" + sc.otherCsId()), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(sc.admin(), get("/api/teacher/classes"), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(sc.s1(), get("/api/teacher/classes"), null).getResponse().getStatus()).isEqualTo(403);

        ok(t, post(base + "/assessments"), assessment("P1", "PROVA", 2, 10));
        ok(t, post(base + "/assessments"), assessment("T1", "TRABALHO", 1, 5));
        JsonNode diary = ok(t, post(base + "/assessments"), assessment("REC", "RECUPERACAO", 1, 10));
        String p1 = assessmentId(diary, "P1"), t1 = assessmentId(diary, "T1"), rec = assessmentId(diary, "REC");
        assertThat(diary.get("students")).hasSize(2);

        assertThat(code(call(t, put(base + "/assessments/" + t1 + "/grades"), Map.of("grades", List.of(Map.of("studentId", sc.s1Id(), "score", 6))))))
                .isEqualTo("SCORE_ABOVE_MAX");
        assertThat(code(call(t, put(base + "/assessments/" + p1 + "/grades"), Map.of("grades", List.of(Map.of("studentId", UUID.randomUUID().toString(), "score", 5))))))
                .isEqualTo("STUDENT_NOT_IN_CLASS");
        assertThat(call(t, put(base + "/assessments/" + p1 + "/grades"), Map.of("grades", List.of(Map.of("studentId", sc.s1Id(), "score", -1))))
                .getResponse().getStatus()).isEqualTo(400);

        ok(t, put(base + "/assessments/" + p1 + "/grades"), Map.of("grades", List.of(Map.of("studentId", sc.s1Id(), "score", 8), Map.of("studentId", sc.s2Id(), "score", 3))));
        ok(t, put(base + "/assessments/" + t1 + "/grades"), Map.of("grades", List.of(Map.of("studentId", sc.s1Id(), "score", 4), Map.of("studentId", sc.s2Id(), "score", 1))));
        diary = ok(t, put(base + "/assessments/" + rec + "/grades"), Map.of("grades", List.of(Map.of("studentId", sc.s2Id(), "score", 7))));
        // s1: (8×2 + 8×1)/3 = 8,00 ; s2: (3×2 + 2×1)/3 = 2,67 e recuperação 7 → final 7,00
        assertThat(row(diary, sc.s1Id()).get("result").get("average").decimalValue()).isEqualByComparingTo("8.00");
        assertThat(row(diary, sc.s2Id()).get("result").get("average").decimalValue()).isEqualByComparingTo("2.67");
        assertThat(row(diary, sc.s2Id()).get("result").get("finalGrade").decimalValue()).isEqualByComparingTo("7");
        assertThat(row(diary, sc.s1Id()).get("result").get("situation").asText()).isEqualTo("EM_CURSO");

        // alteração de nota auditada com valor anterior e novo
        ok(t, put(base + "/assessments/" + p1 + "/grades"), Map.of("grades", List.of(Map.of("studentId", sc.s1Id(), "score", 9.5))));
        List<Map<String, Object>> audits = jdbc.queryForList("select details::text as d from audit_logs where action = 'GRADE_CHANGED' and entity_id = ? order by id", sc.csId());
        assertThat(audits).hasSize(6);
        assertThat(audits.get(5).get("d").toString()).contains("\"before\": \"8.00\"").contains("\"after\": \"9.50\"");
        // avaliação com notas não pode ser excluída; máximo não pode ficar abaixo das notas
        assertThat(code(call(t, delete(base + "/assessments/" + p1), null))).isEqualTo("ASSESSMENT_HAS_GRADES");
        assertThat(code(call(t, put(base + "/assessments/" + p1), assessment("P1", "PROVA", 2, 9)))).isEqualTo("MAX_SCORE_BELOW_GRADES");

        // frequência
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Map<String, Object> session = new HashMap<>(Map.of("date", today.toString(), "lessons", 2, "content", "Limites e derivadas",
                "absences", List.of(Map.of("studentId", sc.s2Id(), "absences", 2, "justified", false))));
        Map<String, Object> future = new HashMap<>(session);
        future.put("date", today.plusDays(1).toString());
        assertThat(code(call(t, post(base + "/attendance"), future))).isEqualTo("FUTURE_DATE");
        Map<String, Object> before = new HashMap<>(session);
        before.put("date", today.minusDays(40).toString());
        assertThat(code(call(t, post(base + "/attendance"), before))).isEqualTo("DATE_OUTSIDE_TERM");
        Map<String, Object> tooMany = new HashMap<>(session);
        tooMany.put("absences", List.of(Map.of("studentId", sc.s2Id(), "absences", 3, "justified", false)));
        assertThat(code(call(t, post(base + "/attendance"), tooMany))).isEqualTo("ABSENCES_ABOVE_LESSONS");
        JsonNode sessions = ok(t, post(base + "/attendance"), session);
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).get("absences")).hasSize(2); // todos os alunos registrados (presença = 0 faltas)
        assertThat(code(call(t, post(base + "/attendance"), session))).isEqualTo("SESSION_EXISTS");
        Map<String, Object> earlier = new HashMap<>(session);
        earlier.put("date", today.minusDays(7).toString());
        earlier.put("absences", List.of());
        ok(t, post(base + "/attendance"), earlier);
        diary = ok(t, get(base), null);
        // s2: 4 aulas, 2 faltas → 50% (abaixo de 75%)
        assertThat(row(diary, sc.s2Id()).get("result").get("attendancePct").decimalValue()).isEqualByComparingTo("50");
        assertThat(row(diary, sc.s2Id()).get("result").get("riskAttendance").asBoolean()).isTrue();
        assertThat(row(diary, sc.s1Id()).get("result").get("attendancePct").decimalValue()).isEqualByComparingTo("100");
        // outro professor não lança na disciplina alheia
        assertThat(call(sc.otherTeacher(), post(base + "/attendance"), session).getResponse().getStatus()).isEqualTo(404);

        // plano de ensino
        assertThat(ok(t, put(base + "/plan"), Map.of("teachingPlan", "Unidade 1: limites")).get("teachingPlan").asText()).isEqualTo("Unidade 1: limites");

        // exportações
        MvcResult xlsx = call(t, get(base + "/export").param("format", "xlsx"), null);
        assertThat(xlsx.getResponse().getContentType()).contains("spreadsheetml");
        assertThat(xlsx.getResponse().getContentAsByteArray().length).isGreaterThan(1000);
        MvcResult csv = call(t, get(base + "/export").param("format", "csv"), null);
        assertThat(csv.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Aluno Um").contains("Em curso");

        // encerramento do período: situação final e bloqueio de lançamentos
        ok(sc.admin(), post("/api/academic/terms/" + sc.termId() + "/status"), Map.of("status", "CLOSED"));
        diary = ok(t, get(base), null);
        assertThat(diary.get("editable").asBoolean()).isFalse();
        assertThat(row(diary, sc.s1Id()).get("result").get("situation").asText()).isEqualTo("APROVADO");
        assertThat(row(diary, sc.s2Id()).get("result").get("situation").asText()).isEqualTo("REPROVADO_FREQUENCIA");
        assertThat(code(call(t, put(base + "/assessments/" + p1 + "/grades"), Map.of("grades", List.of(Map.of("studentId", sc.s1Id(), "score", 10))))))
                .isEqualTo("TERM_NOT_OPEN");
        assertThat(call(t, get(base + "/export").param("format", "csv"), null).getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("Reprovado por frequência");
    }

    @Test
    void studentSeesOnlyOwnAcademicData() throws Exception {
        Scenario sc = scenario();
        String base = "/api/teacher/class-subjects/" + sc.csId();
        JsonNode d = ok(sc.teacher(), post(base + "/assessments"), assessment("P1", "PROVA", 1, 10));
        String p1 = assessmentId(d, "P1");
        ok(sc.teacher(), put(base + "/assessments/" + p1 + "/grades"), Map.of("grades", List.of(Map.of("studentId", sc.s1Id(), "score", 9), Map.of("studentId", sc.s2Id(), "score", 4))));

        JsonNode ov = ok(sc.s1(), get("/api/student/overview"), null);
        assertThat(ov.get("courseName").asText()).isEqualTo("Engenharia");
        assertThat(ov.get("classCode").asText()).isEqualTo("ENG1");
        assertThat(ov.get("schedule")).hasSize(2);
        JsonNode calc = null;
        for (JsonNode s : ov.get("subjects")) if (s.get("subjectCode").asText().equals("CALC1")) calc = s;
        assertThat(calc.get("result").get("finalGrade").decimalValue()).isEqualByComparingTo("9");
        assertThat(calc.get("teacherName").asText()).isEqualTo("Prof Titular");

        JsonNode detail = ok(sc.s1(), get("/api/student/subjects/" + sc.csId()), null);
        assertThat(detail.get("assessments").get(0).get("score").decimalValue()).isEqualByComparingTo("9");
        assertThat(detail.toString()).doesNotContain("Aluno Dois");
        assertThat(call(sc.s1(), get("/api/student/subjects/" + UUID.randomUUID()), null).getResponse().getStatus()).isEqualTo(404);
        // o aluno não acessa o portal do professor nem cadastros; professor não usa o portal do aluno
        assertThat(call(sc.s1(), get(base), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(sc.teacher(), get("/api/student/overview"), null).getResponse().getStatus()).isEqualTo(403);

        JsonNode cur = ok(sc.s1(), get("/api/student/curriculum"), null);
        assertThat(cur.get("items")).hasSize(2);
        assertThat(cur.get("items").get(0).get("status").asText()).isEqualTo("CURSANDO");
        assertThat(cur.get("completionPct").decimalValue()).isEqualByComparingTo("0");

        ok(sc.admin(), post("/api/academic/terms/" + sc.termId() + "/status"), Map.of("status", "CLOSED"));
        cur = ok(sc.s1(), get("/api/student/curriculum"), null);
        Map<String, String> status = new HashMap<>();
        cur.get("items").forEach(i -> status.put(i.get("subjectCode").asText(), i.get("status").asText()));
        assertThat(status).containsEntry("CALC1", "APROVADA").containsEntry("FIS1", "REPROVADA"); // Física sem notas
        assertThat(cur.get("completedHours").asInt()).isEqualTo(80);
        JsonNode hist = ok(sc.s2(), get("/api/student/history"), null);
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).get("subjects").toString()).contains("REPROVADO_NOTA");
    }
}
