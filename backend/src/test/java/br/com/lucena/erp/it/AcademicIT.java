package br.com.lucena.erp.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Year;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Fase 4: cursos, disciplinas, matriz, períodos, turmas, professores, alunos e funcionários. */
class AcademicIT extends IntegrationTestBase {
    static final String PWD = "Gestao#Segura2026";

    @Autowired
    JdbcTemplate jdbc;

    record Fac(String slug, String id, Session admin) {
    }

    private Fac readyFaculty() throws Exception {
        String slug = "acad-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode f = createFaculty(master(), slug, "diretor");
        String temp = f.get("initialAdminTemporaryPassword").asText();
        Session s = changePassword(login(slug, "diretor", temp), slug, "diretor", temp, PWD);
        return new Fac(slug, f.get("faculty").get("id").asText(), s);
    }

    /** Usuário pronto (MFA + senha definitiva) com o cargo informado. */
    private Session userWithRole(Fac f, String username, String roleCode) throws Exception {
        String roleId = null;
        for (JsonNode r : body(call(f.admin(), get("/api/admin/roles"), null))) if (r.get("code").asText().equals(roleCode)) roleId = r.get("id").asText();
        JsonNode created = body(call(f.admin(), post("/api/admin/users"), Map.of("username", username, "fullName", "Usuário " + username,
                "email", username + "@x.edu.br", "roleIds", List.of(roleId))));
        String temp = created.get("temporaryPassword").asText();
        return changePassword(login(f.slug(), username, temp), f.slug(), username, temp, PWD);
    }

    private MvcResult req(Session s, MockHttpServletRequestBuilder b, Object payload) throws Exception {
        return call(s, b, payload);
    }

    private JsonNode ok(Session s, MockHttpServletRequestBuilder b, Object payload) throws Exception {
        MvcResult r = call(s, b, payload);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isBetween(200, 299);
        return body(r);
    }

    private String code(MvcResult r) throws Exception {
        return body(r).get("code").asText();
    }

    private Map<String, Object> course(String code, int semesters) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", "Curso " + code);
        m.put("degree", "BACHARELADO");
        m.put("modality", "PRESENCIAL");
        m.put("durationSemesters", semesters);
        m.put("monthlyFee", 1250.50);
        m.put("active", true);
        return m;
    }

    /** Disciplina criada dentro do curso (cadastro encadeado). */
    private Map<String, Object> subject(String code, String courseId, int semester) {
        return Map.of("code", code, "name", "Disciplina " + code, "workloadHours", 60, "credits", 4, "active", true,
                "courseId", courseId, "semester", semester);
    }

    private String newCourse(Session s, String code) throws Exception {
        return ok(s, post("/api/academic/courses"), course(code, 8)).get("id").asText();
    }

    private Map<String, Object> student(String name, String cpf, boolean access, String courseId) {
        Map<String, Object> m = new HashMap<>();
        m.put("courseId", courseId);
        m.put("fullName", name);
        m.put("cpf", cpf);
        m.put("email", name.toLowerCase().replace(" ", ".") + "@aluno.edu.br");
        m.put("createAccess", access);
        return m;
    }

    private Map<String, Object> teacher(String name, String username) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", name);
        m.put("cpf", nextCpf());
        m.put("email", name.toLowerCase().replace(" ", ".") + "@prof.edu.br");
        m.put("status", "ACTIVE");
        if (username != null) m.put("username", username);
        return m;
    }

    private Map<String, Object> offer(String subjectId, String teacherId, int weekday, String start, String end) {
        Map<String, Object> m = new HashMap<>();
        m.put("subjectId", subjectId);
        m.put("teacherId", teacherId);
        m.put("weekday", weekday);
        m.put("startTime", start);
        m.put("endTime", end);
        return m;
    }

    @Test
    void courseSubjectAndCurriculumRules() throws Exception {
        Fac f = readyFaculty();
        Session s = f.admin();
        String courseId = ok(s, post("/api/academic/courses"), course("ADS", 4)).get("id").asText();
        assertThat(code(req(s, post("/api/academic/courses"), course("ads", 4)))).isEqualTo("COURSE_CODE_IN_USE");
        Map<String, Object> bad = course("X1", 4);
        bad.put("durationSemesters", 0);
        assertThat(req(s, post("/api/academic/courses"), bad).getResponse().getStatus()).isEqualTo(400);

        String other = ok(s, post("/api/academic/courses"), course("OUT", 4)).get("id").asText();
        // não existe disciplina sem curso
        assertThat(code(req(s, post("/api/academic/subjects"), Map.of("code", "SOLTA", "name", "Solta", "workloadHours", 60, "credits", 4, "active", true))))
                .isEqualTo("COURSE_REQUIRED");
        // a disciplina nasce na matriz do curso escolhido
        JsonNode algView = ok(s, post("/api/academic/subjects"), subject("ALG1", courseId, 1));
        String alg = algView.get("id").asText();
        assertThat(algView.get("courses").get(0).get("code").asText()).isEqualTo("ADS");
        assertThat(code(req(s, post("/api/academic/subjects"), subject("alg1", courseId, 1)))).isEqualTo("SUBJECT_CODE_IN_USE");
        assertThat(code(req(s, post("/api/academic/subjects"), subject("X9", courseId, 5)))).isEqualTo("SEMESTER_OUT_OF_RANGE");
        String alg2 = ok(s, post("/api/academic/subjects"), subject("ALG2", other, 1)).get("id").asText();
        String bd = ok(s, post("/api/academic/subjects"), subject("BD1", other, 1)).get("id").asText();
        assertThat(ok(s, get("/api/academic/subjects").param("courseId", courseId), null).get("totalElements").asLong()).isEqualTo(1);

        String base = "/api/academic/courses/" + courseId + "/curriculum";
        JsonNode cur = ok(s, get(base), null);
        String algItem = cur.get("items").get(0).get("id").asText();
        // semestre além da duração do curso
        assertThat(code(req(s, post(base), Map.of("subjectId", bd, "semester", 5, "mandatory", true)))).isEqualTo("SEMESTER_OUT_OF_RANGE");
        // pré-requisito precisa estar em semestre anterior
        assertThat(code(req(s, post(base), Map.of("subjectId", alg2, "semester", 1, "mandatory", true, "prerequisiteSubjectId", alg)))).isEqualTo("PREREQUISITE_ORDER");
        // pré-requisito fora da matriz
        assertThat(code(req(s, post(base), Map.of("subjectId", alg2, "semester", 2, "mandatory", true, "prerequisiteSubjectId", bd)))).isEqualTo("INVALID_PREREQUISITE");
        cur = ok(s, post(base), Map.of("subjectId", alg2, "semester", 2, "mandatory", true, "prerequisiteSubjectId", alg));
        assertThat(cur.get("items")).hasSize(2);
        assertThat(cur.get("totalHours").asInt()).isEqualTo(120);
        assertThat(cur.get("totalCredits").asInt()).isEqualTo(8);
        assertThat(code(req(s, post(base), Map.of("subjectId", alg2, "semester", 3, "mandatory", true)))).isEqualTo("SUBJECT_ALREADY_IN_CURRICULUM");
        // mover o pré-requisito para depois do dependente é recusado; removê-lo também
        assertThat(code(req(s, put(base + "/" + algItem), Map.of("subjectId", alg, "semester", 3, "mandatory", true)))).isEqualTo("PREREQUISITE_ORDER");
        assertThat(code(req(s, delete(base + "/" + algItem), null))).isEqualTo("SUBJECT_IS_PREREQUISITE");
        // disciplina em uso não pode ser excluída; duração não pode ficar menor que a matriz
        assertThat(code(req(s, delete("/api/academic/subjects/" + alg), null))).isEqualTo("SUBJECT_IN_USE");
        assertThat(code(req(s, put("/api/academic/courses/" + courseId), course("ADS", 1)))).isEqualTo("DURATION_TOO_SHORT");
        // disciplina sem uso (fora de qualquer matriz) pode ser excluída
        assertThat(code(req(s, delete("/api/academic/subjects/" + bd), null))).isEqualTo("SUBJECT_IN_USE");
        String bdItem = null;
        for (JsonNode i : ok(s, get("/api/academic/courses/" + other + "/curriculum"), null).get("items")) {
            if (i.get("subjectId").asText().equals(bd)) bdItem = i.get("id").asText();
        }
        ok(s, delete("/api/academic/courses/" + other + "/curriculum/" + bdItem), null);
        assertThat(req(s, delete("/api/academic/subjects/" + bd), null).getResponse().getStatus()).isEqualTo(204);

        Integer audited = jdbc.queryForObject("select count(*) from audit_logs where action = 'CURRICULUM_CHANGED' and entity_id = ?", Integer.class, courseId);
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void classesScheduleCapacityAndTermRules() throws Exception {
        Fac f = readyFaculty();
        Session s = f.admin();
        String courseId = ok(s, post("/api/academic/courses"), course("DIR", 10)).get("id").asText();
        String otherCourse = newCourse(s, "MED");
        String sub1 = ok(s, post("/api/academic/subjects"), subject("DCIV", courseId, 1)).get("id").asText();
        String sub2 = ok(s, post("/api/academic/subjects"), subject("DPEN", courseId, 1)).get("id").asText();
        String outside = ok(s, post("/api/academic/subjects"), subject("FORA", otherCourse, 1)).get("id").asText();

        assertThat(code(req(s, post("/api/academic/terms"), Map.of("code", "2026.1", "startDate", "2026-07-01", "endDate", "2026-02-01")))).isEqualTo("INVALID_DATES");
        assertThat(req(s, post("/api/academic/terms"), Map.of("code", "26-1", "startDate", "2026-02-01", "endDate", "2026-07-01")).getResponse().getStatus()).isEqualTo(400);
        String termId = ok(s, post("/api/academic/terms"), Map.of("code", "2026.1", "startDate", "2026-02-01", "endDate", "2026-07-01")).get("id").asText();
        assertThat(code(req(s, post("/api/academic/terms/" + termId + "/status"), Map.of("status", "CLOSED")))).isEqualTo("INVALID_TRANSITION");
        ok(s, post("/api/academic/terms/" + termId + "/status"), Map.of("status", "OPEN"));

        Map<String, Object> cls = new HashMap<>(Map.of("courseId", courseId, "termId", termId, "code", "DIR1A", "semester", 1,
                "shift", "NOITE", "capacity", 2, "room", "101", "active", true));
        String classA = ok(s, post("/api/academic/classes"), cls).get("id").asText();
        assertThat(code(req(s, post("/api/academic/classes"), cls))).isEqualTo("CLASS_CODE_IN_USE");
        Map<String, Object> clsB = new HashMap<>(cls);
        clsB.put("code", "DIR1B");
        clsB.put("room", "102");
        String classB = ok(s, post("/api/academic/classes"), clsB).get("id").asText();
        Map<String, Object> badSem = new HashMap<>(cls);
        badSem.put("code", "DIR11");
        badSem.put("semester", 11);
        assertThat(code(req(s, post("/api/academic/classes"), badSem))).isEqualTo("SEMESTER_OUT_OF_RANGE");

        String t1 = ok(s, post("/api/academic/teachers"), teacher("Paulo Professor", null)).get("record").get("id").asText();
        String t2 = ok(s, post("/api/academic/teachers"), teacher("Rita Professora", null)).get("record").get("id").asText();

        String subjA = "/api/academic/classes/" + classA + "/subjects";
        assertThat(code(req(s, post(subjA), offer(outside, t1, 1, "19:00", "20:40")))).isEqualTo("SUBJECT_NOT_IN_CURRICULUM");
        assertThat(code(req(s, post(subjA), offer(sub1, t1, 1, "20:40", "19:00")))).isEqualTo("INVALID_SCHEDULE");
        Map<String, Object> partial = new HashMap<>(Map.of("subjectId", sub1, "weekday", 1));
        assertThat(code(req(s, post(subjA), partial))).isEqualTo("INCOMPLETE_SCHEDULE");
        JsonNode detail = ok(s, post(subjA), offer(sub1, t1, 1, "19:00", "20:40"));
        assertThat(detail.get("subjects").get(0).get("teacherName").asText()).isEqualTo("Paulo Professor");
        assertThat(code(req(s, post(subjA), offer(sub1, t2, 2, "19:00", "20:40")))).isEqualTo("SUBJECT_ALREADY_OFFERED");
        // a própria turma não pode ter duas aulas simultâneas
        assertThat(code(req(s, post(subjA), offer(sub2, t2, 1, "20:00", "21:00")))).isEqualTo("CLASS_SCHEDULE_CONFLICT");
        // professor não pode estar em duas turmas ao mesmo tempo
        assertThat(code(req(s, post("/api/academic/classes/" + classB + "/subjects"), offer(sub1, t1, 1, "20:00", "22:00")))).isEqualTo("TEACHER_SCHEDULE_CONFLICT");
        // sala não pode ser usada por duas turmas ao mesmo tempo
        Map<String, Object> sameRoom = new HashMap<>(clsB);
        sameRoom.put("room", "101");
        ok(s, post("/api/academic/classes/" + classB + "/subjects"), offer(sub1, t2, 1, "19:30", "21:00"));
        assertThat(code(req(s, put("/api/academic/classes/" + classB), sameRoom))).isEqualTo("ROOM_SCHEDULE_CONFLICT");
        // horário adjacente (sem sobreposição) é permitido
        ok(s, post(subjA), offer(sub2, t1, 1, "20:40", "22:20"));

        // alunos: capacidade e uma turma por período
        String[] st = new String[3];
        for (int i = 0; i < 3; i++) st[i] = ok(s, post("/api/academic/students"), student("Aluno " + i, nextCpf(), false, courseId)).get("record").get("id").asText();
        // aluno de outro curso não entra na turma
        String medStudent = ok(s, post("/api/academic/students"), student("Aluno Medicina", nextCpf(), false, otherCourse)).get("record").get("id").asText();
        String studA = "/api/academic/classes/" + classA + "/students";
        assertThat(code(req(s, post(studA), Map.of("studentId", medStudent)))).isEqualTo("STUDENT_COURSE_MISMATCH");
        ok(s, post(studA), Map.of("studentId", st[0]));
        assertThat(code(req(s, post("/api/academic/classes/" + classB + "/students"), Map.of("studentId", st[0])))).isEqualTo("STUDENT_ALREADY_IN_TERM");
        ok(s, post(studA), Map.of("studentId", st[1]));
        assertThat(code(req(s, post(studA), Map.of("studentId", st[2])))).isEqualTo("CLASS_FULL");
        Map<String, Object> smaller = new HashMap<>(cls);
        smaller.put("capacity", 1);
        assertThat(code(req(s, put("/api/academic/classes/" + classA), smaller))).isEqualTo("CAPACITY_BELOW_ENROLLED");
        assertThat(code(req(s, delete("/api/academic/classes/" + classA), null))).isEqualTo("CLASS_IN_USE");
        // aluno inativo não entra em turma
        ok(s, post("/api/academic/students/" + st[2] + "/status"), Map.of("status", "LOCKED", "reason", "Trancamento solicitado"));
        assertThat(code(req(s, post("/api/academic/classes/" + classB + "/students"), Map.of("studentId", st[2])))).isEqualTo("STUDENT_NOT_ACTIVE");

        JsonNode d = ok(s, get("/api/academic/classes/" + classA), null);
        assertThat(d.get("students")).hasSize(2);
        assertThat(d.get("turma").get("students").asInt()).isEqualTo(2);

        // período encerrado bloqueia alterações
        ok(s, post("/api/academic/terms/" + termId + "/status"), Map.of("status", "CLOSED"));
        assertThat(code(req(s, delete(studA + "/" + st[0]), null))).isEqualTo("TERM_CLOSED");
        assertThat(code(req(s, post(subjA), offer(sub2, t2, 3, "19:00", "20:00")))).isEqualTo("TERM_CLOSED");
        // professor com aulas não pode ser excluído; curso com turmas também não
        assertThat(code(req(s, delete("/api/academic/teachers/" + t1), null))).isEqualTo("TEACHER_IN_USE");
        assertThat(code(req(s, delete("/api/academic/courses/" + courseId), null))).isEqualTo("COURSE_IN_USE");
    }

    @Test
    void studentRegistrationAccessAndLoginWithMatricula() throws Exception {
        Fac f = readyFaculty();
        Session s = f.admin();
        String courseId = newCourse(s, "ENF");
        // não existe aluno sem curso
        Map<String, Object> noCourse = student("Sem Curso", nextCpf(), false, null);
        assertThat(req(s, post("/api/academic/students"), noCourse).getResponse().getStatus()).isEqualTo(400);
        assertThat(code(req(s, post("/api/academic/students"), student("Ana Silva", "12345678900", true, courseId)))).isEqualTo("INVALID_CPF");
        String cpf = nextCpf();
        JsonNode created = ok(s, post("/api/academic/students"), student("Ana Silva", cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9), true, courseId));
        String year = String.valueOf(Year.now().getValue());
        String registration = created.get("record").get("registration").asText();
        assertThat(registration).isEqualTo(year + "00001");
        assertThat(created.get("username").asText()).isEqualTo(registration);
        assertThat(created.get("record").get("cpf").asText()).isEqualTo(cpf);
        assertThat(code(req(s, post("/api/academic/students"), student("Outra Pessoa", cpf, false, courseId)))).isEqualTo("CPF_IN_USE");
        JsonNode second = ok(s, post("/api/academic/students"), student("Bruno Lima", nextCpf(), false, courseId));
        assertThat(second.get("record").get("registration").asText()).isEqualTo(year + "00002");
        assertThat(second.get("temporaryPassword")).isNull();
        // gerar acesso depois, e não duas vezes
        String brunoId = second.get("record").get("id").asText();
        assertThat(ok(s, post("/api/academic/students/" + brunoId + "/access"), null).get("temporaryPassword").asText()).isNotBlank();
        assertThat(code(req(s, post("/api/academic/students/" + brunoId + "/access"), null))).isEqualTo("ACCESS_EXISTS");
        // matrícula não pode ser alterada
        Map<String, Object> upd = student("Ana Souza Silva", cpf, false, courseId);
        upd.put("registration", "OUTRA123");
        assertThat(code(req(s, put("/api/academic/students/" + created.get("record").get("id").asText()), upd))).isEqualTo("REGISTRATION_IMMUTABLE");

        // busca por matrícula e por CPF formatado
        assertThat(ok(s, get("/api/academic/students").param("q", registration), null).get("totalElements").asLong()).isEqualTo(1);
        assertThat(ok(s, get("/api/academic/students").param("q", cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9)), null)
                .get("totalElements").asLong()).isEqualTo(1);

        // O aluno entra com a MATRÍCULA, configura o autenticador e recebe somente o portal do aluno
        String temp = created.get("temporaryPassword").asText();
        Session aluno = changePassword(login(f.slug(), registration, temp), f.slug(), registration, temp, "Estudante#Forte2026");
        JsonNode me = body(call(aluno, get("/api/me"), null));
        assertThat(me.get("registration").asText()).isEqualTo(registration);
        assertThat(me.get("permissions").toString()).contains("STUDENT_PORTAL").doesNotContain("COURSE_VIEW");
        assertThat(req(aluno, get("/api/academic/courses"), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(req(aluno, get("/api/academic/students"), null).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void rolePermissionsAreEnforced() throws Exception {
        Fac f = readyFaculty();
        Session secretaria = userWithRole(f, "secretaria", "SECRETARIA");
        Session coordenador = userWithRole(f, "coordenador", "COORDENADOR");
        Session professor = userWithRole(f, "professor", "PROFESSOR");
        String courseId = newCourse(f.admin(), "ADM");

        // Secretaria cadastra aluno, mas não cria curso
        assertThat(req(secretaria, post("/api/academic/students"), student("Carla Dias", nextCpf(), false, courseId)).getResponse().getStatus()).isEqualTo(201);
        assertThat(req(secretaria, post("/api/academic/courses"), course("SEC", 4)).getResponse().getStatus()).isEqualTo(403);
        // Coordenação cria curso e professor (com acesso), mas não cadastra aluno e vê CPF de aluno mascarado
        assertThat(req(coordenador, post("/api/academic/courses"), course("COO", 4)).getResponse().getStatus()).isEqualTo(201);
        assertThat(req(coordenador, post("/api/academic/students"), student("Davi Reis", nextCpf(), false, courseId)).getResponse().getStatus()).isEqualTo(403);
        JsonNode list = ok(coordenador, get("/api/academic/students"), null);
        assertThat(list.get("content").get(0).get("cpf")).isNull();
        assertThat(list.get("content").get(0).get("cpfMasked").asText()).startsWith("***.");
        JsonNode prof = ok(coordenador, post("/api/academic/teachers"), teacher("Lia Docente", "lia.docente"));
        assertThat(prof.get("temporaryPassword").asText()).isNotBlank();
        Session lia = login(f.slug(), "lia.docente", prof.get("temporaryPassword").asText());
        assertThat(body(call(lia, get("/api/me"), null)).get("roles").toString()).contains("Professor");
        // Professor não acessa cadastros
        assertThat(req(professor, get("/api/academic/teachers"), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(req(professor, get("/api/academic/students"), null).getResponse().getStatus()).isEqualTo(403);

        // Funcionário com acesso: só cargos que o operador pode conceder
        String finRole = null;
        for (JsonNode r : body(call(f.admin(), get("/api/admin/roles"), null))) if (r.get("code").asText().equals("FINANCEIRO")) finRole = r.get("id").asText();
        Map<String, Object> emp = new HashMap<>(Map.of("fullName", "Fábio Tesoureiro", "cpf", nextCpf(), "email", "fabio@x.edu.br",
                "status", "ACTIVE", "username", "fabio.tes", "roleIds", List.of(finRole)));
        JsonNode e = ok(f.admin(), post("/api/academic/employees"), emp);
        assertThat(e.get("record").get("hasAccess").asBoolean()).isTrue();
        assertThat(code(req(f.admin(), delete("/api/academic/employees/" + e.get("record").get("id").asText()), null))).isEqualTo("EMPLOYEE_HAS_ACCESS");
    }

    @Test
    void academicDataIsIsolatedBetweenFaculties() throws Exception {
        Fac a = readyFaculty();
        Fac b = readyFaculty();
        String courseA = ok(a.admin(), post("/api/academic/courses"), course("ISO", 4)).get("id").asText();
        String subjectA = ok(a.admin(), post("/api/academic/subjects"), subject("ISO1", courseA, 1)).get("id").asText();
        String studentA = ok(a.admin(), post("/api/academic/students"), student("Eva Isolada", nextCpf(), false, courseA)).get("record").get("id").asText();

        String courseB = ok(b.admin(), post("/api/academic/courses"), course("ISO", 4)).get("id").asText(); // mesmo código em outra faculdade
        String termB = ok(b.admin(), post("/api/academic/terms"), Map.of("code", "2026.2", "startDate", "2026-08-01", "endDate", "2026-12-15")).get("id").asText();
        String classB = ok(b.admin(), post("/api/academic/classes"), Map.of("courseId", courseB, "termId", termB, "code", "T1", "semester", 1,
                "shift", "MANHA", "capacity", 30, "active", true)).get("id").asText();

        assertThat(req(b.admin(), get("/api/academic/courses/" + courseA), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(req(b.admin(), get("/api/academic/students/" + studentA), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(req(b.admin(), post("/api/academic/courses/" + courseB + "/curriculum"), Map.of("subjectId", subjectA, "semester", 1, "mandatory", true))
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(req(b.admin(), post("/api/academic/classes/" + classB + "/students"), Map.of("studentId", studentA)).getResponse().getStatus()).isEqualTo(404);
        // curso de outra faculdade não serve para cadastrar aluno/disciplina
        assertThat(code(req(b.admin(), post("/api/academic/students"), student("Intruso", nextCpf(), false, courseA)))).isEqualTo("INVALID_COURSE");
        assertThat(req(b.admin(), post("/api/academic/subjects"), subject("INV1", courseA, 1)).getResponse().getStatus()).isEqualTo(404);
        assertThat(req(b.admin(), post("/api/academic/classes"), Map.of("courseId", courseA, "termId", termB, "code", "X", "semester", 1,
                "shift", "MANHA", "capacity", 30, "active", true)).getResponse().getStatus()).isEqualTo(404);
        assertThat(req(b.admin(), get("/api/academic/courses").param("facultyId", a.id()), null).getResponse().getStatus()).isEqualTo(403);
        assertThat(ok(b.admin(), get("/api/academic/students"), null).get("totalElements").asLong()).isZero();
        // CPF pode repetir entre faculdades (cada uma é um ambiente independente)
        String cpf = jdbc.queryForObject("select cpf from students where id = ?::uuid", String.class, studentA);
        assertThat(req(b.admin(), post("/api/academic/students"), student("Eva Isolada", cpf, false, courseB)).getResponse().getStatus()).isEqualTo(201);
        // Master opera informando a faculdade
        assertThat(ok(master(), get("/api/academic/courses").param("facultyId", a.id()), null).get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void planLimitsAreEnforced() throws Exception {
        Fac f = readyFaculty();
        UUID plan = UUID.randomUUID();
        jdbc.update("insert into plans (id, code, name, max_students, max_users, monthly_price) values (?, ?, 'Teste', 1, 50, 0)",
                plan, "T" + plan.toString().substring(0, 8).toUpperCase());
        jdbc.update("update faculties set plan_id = ? where id = ?::uuid", plan, f.id());
        String courseId = newCourse(f.admin(), "PLN");
        ok(f.admin(), post("/api/academic/students"), student("Primeiro Aluno", nextCpf(), false, courseId));
        MvcResult r = req(f.admin(), post("/api/academic/students"), student("Segundo Aluno", nextCpf(), false, courseId));
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(code(r)).isEqualTo("PLAN_LIMIT_REACHED");
    }

    @Test
    void chainedRegistrationAndPendingChecklist() throws Exception {
        Fac f = readyFaculty();
        Session s = f.admin();
        java.util.function.Function<String, Long> pendingCount = key -> {
            try {
                for (JsonNode p : ok(s, get("/api/academic/pending"), null)) if (p.get("key").asText().equals(key)) return p.get("count").asLong();
                return 0L;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        // 1º passo pendente: curso
        assertThat(pendingCount.apply("NO_COURSE")).isEqualTo(1);
        String course = newCourse(s, "ARQ");
        assertThat(pendingCount.apply("NO_COURSE")).isZero();
        assertThat(pendingCount.apply("COURSE_WITHOUT_SUBJECTS")).isEqualTo(1);
        // 2º: disciplina dentro do curso
        String sub = ok(s, post("/api/academic/subjects"), subject("PROJ1", course, 1)).get("id").asText();
        assertThat(pendingCount.apply("COURSE_WITHOUT_SUBJECTS")).isZero();
        assertThat(pendingCount.apply("NO_OPEN_TERM")).isEqualTo(1);
        assertThat(pendingCount.apply("COURSE_WITHOUT_CLASS")).isEqualTo(1);
        // 3º: período e turma
        String term = ok(s, post("/api/academic/terms"), Map.of("code", "2026.2", "startDate", "2026-08-01", "endDate", "2026-12-15")).get("id").asText();
        ok(s, post("/api/academic/terms/" + term + "/status"), Map.of("status", "OPEN"));
        String turma = ok(s, post("/api/academic/classes"), Map.of("courseId", course, "termId", term, "code", "ARQ1", "semester", 1,
                "shift", "NOITE", "capacity", 30, "active", true)).get("id").asText();
        assertThat(pendingCount.apply("CLASS_WITHOUT_SUBJECTS")).isEqualTo(1);
        ok(s, post("/api/academic/classes/" + turma + "/subjects"), Map.of("subjectId", sub));
        assertThat(pendingCount.apply("OFFER_WITHOUT_TEACHER")).isEqualTo(1);
        assertThat(pendingCount.apply("OFFER_WITHOUT_SCHEDULE")).isEqualTo(1);
        // 4º: professor atribuído com horário
        String prof = ok(s, post("/api/academic/teachers"), teacher("Rui Arquiteto", null)).get("record").get("id").asText();
        String cs = ok(s, get("/api/academic/classes/" + turma), null).get("subjects").get(0).get("id").asText();
        ok(s, put("/api/academic/classes/" + turma + "/subjects/" + cs), offer(sub, prof, 2, "19:00", "21:00"));
        assertThat(pendingCount.apply("OFFER_WITHOUT_TEACHER")).isZero();
        assertThat(pendingCount.apply("CLASS_WITHOUT_STUDENTS")).isEqualTo(1);
        // 5º: aluno do curso, incluído na turma já no cadastro
        Map<String, Object> st = student("Lia Aluna", nextCpf(), false, course);
        st.put("classId", turma);
        JsonNode created = ok(s, post("/api/academic/students"), st);
        assertThat(created.get("record").get("courseName").asText()).isEqualTo("Curso ARQ");
        assertThat(created.get("record").get("currentClass").asText()).startsWith("ARQ1");
        assertThat(pendingCount.apply("CLASS_WITHOUT_STUDENTS")).isZero();
        assertThat(pendingCount.apply("STUDENT_WITHOUT_CLASS")).isZero();
        assertThat(pendingCount.apply("STUDENT_WITHOUT_ACCESS")).isEqualTo(1);
        assertThat(ok(s, get("/api/academic/students").param("courseId", course), null).get("totalElements").asLong()).isEqualTo(1);
        // turma de outro curso no cadastro é recusada (e nada é gravado)
        String other = newCourse(s, "DES");
        Map<String, Object> wrong = student("Teo Errado", nextCpf(), false, other);
        wrong.put("classId", turma);
        assertThat(code(req(s, post("/api/academic/students"), wrong))).isEqualTo("STUDENT_COURSE_MISMATCH");
        assertThat(ok(s, get("/api/academic/students").param("q", "Teo Errado"), null).get("totalElements").asLong()).isZero();
        // trocar o curso exige tirar o aluno da turma do curso atual
        String lia = created.get("record").get("id").asText();
        Map<String, Object> move = student("Lia Aluna", jdbc.queryForObject("select cpf from students where id = ?::uuid", String.class, lia), false, other);
        assertThat(code(req(s, put("/api/academic/students/" + lia), move))).isEqualTo("STUDENT_IN_CLASS_OF_COURSE");
        ok(s, delete("/api/academic/classes/" + turma + "/students/" + lia), null);
        assertThat(ok(s, put("/api/academic/students/" + lia), move).get("courseName").asText()).isEqualTo("Curso DES");
    }
}
