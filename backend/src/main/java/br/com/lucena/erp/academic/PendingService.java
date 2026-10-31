package br.com.lucena.erp.academic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pendências do cadastro acadêmico, seguindo a ordem curso → disciplina → turma → professor → aluno.
 * Cada pendência traz o próximo passo e o link da tela onde resolvê-la.
 */
@Service
@RequiredArgsConstructor
public class PendingService {
    static final int LIMIT = 50;

    private final JdbcTemplate jdbc;

    public enum Severity { ALTA, MEDIA, BAIXA }

    public record Item(String label, String link) {
    }

    public record Pending(String key, int step, Severity severity, String title, String action, String link, long count, List<Item> items) {
    }

    @Transactional(readOnly = true)
    public List<Pending> list(UUID fid) {
        List<Pending> out = new ArrayList<>();
        long courses = count("select count(*) from courses where faculty_id = ? and active", fid);
        if (courses == 0) {
            out.add(new Pending("NO_COURSE", 1, Severity.ALTA, "Nenhum curso cadastrado", "Cadastrar o primeiro curso", "/academico/cursos", 1, List.of()));
        }
        add(out, "COURSE_WITHOUT_SUBJECTS", 2, Severity.ALTA, "Cursos sem disciplinas na matriz curricular", "Cadastrar as disciplinas do curso", """
                select c.code || ' — ' || c.name, '/academico/cursos/' || c.id from courses c
                where c.faculty_id = ? and c.active and not exists (select 1 from curriculum cu where cu.course_id = c.id) order by c.name""", fid);
        add(out, "SUBJECT_WITHOUT_COURSE", 2, Severity.BAIXA, "Disciplinas fora de qualquer matriz curricular", "Adicionar a disciplina à matriz de um curso", """
                select s.code || ' — ' || s.name, '/academico/disciplinas' from subjects s
                where s.faculty_id = ? and s.active and not exists (select 1 from curriculum cu where cu.subject_id = s.id) order by s.name""", fid);
        long openTerms = count("select count(*) from academic_terms where faculty_id = ? and status = 'OPEN'", fid);
        if (openTerms == 0) {
            out.add(new Pending("NO_OPEN_TERM", 3, Severity.ALTA, "Nenhum período letivo aberto",
                    "Cadastrar e abrir o período letivo (necessário para lançar notas e frequência)", "/academico/periodos", 1, List.of()));
        }
        add(out, "COURSE_WITHOUT_CLASS", 3, Severity.MEDIA, "Cursos com matriz, mas sem turma no período", "Criar a turma do curso", """
                select c.code || ' — ' || c.name, '/academico/turmas' from courses c
                where c.faculty_id = ? and c.active and exists (select 1 from curriculum cu where cu.course_id = c.id)
                  and not exists (select 1 from classes k join academic_terms t on t.id = k.term_id where k.course_id = c.id and t.status <> 'CLOSED')
                order by c.name""", fid);
        add(out, "CLASS_WITHOUT_SUBJECTS", 3, Severity.ALTA, "Turmas sem disciplinas ofertadas", "Ofertar as disciplinas da matriz na turma", """
                select k.code || ' (' || t.code || ')', '/academico/turmas/' || k.id from classes k join academic_terms t on t.id = k.term_id
                where k.faculty_id = ? and k.active and t.status <> 'CLOSED' and not exists (select 1 from class_subjects cs where cs.class_id = k.id)
                order by t.code, k.code""", fid);
        if (count("select count(*) from teachers where faculty_id = ? and status = 'ACTIVE'", fid) == 0
                && count("select count(*) from classes where faculty_id = ?", fid) > 0) {
            out.add(new Pending("NO_TEACHER", 4, Severity.ALTA, "Nenhum professor cadastrado", "Cadastrar os professores", "/academico/professores", 1, List.of()));
        }
        add(out, "OFFER_WITHOUT_TEACHER", 4, Severity.ALTA, "Disciplinas ofertadas sem professor", "Atribuir o professor na turma", """
                select k.code || ' — ' || s.name, '/academico/turmas/' || k.id from class_subjects cs join classes k on k.id = cs.class_id
                join academic_terms t on t.id = k.term_id join subjects s on s.id = cs.subject_id
                where cs.faculty_id = ? and cs.teacher_id is null and t.status <> 'CLOSED' order by k.code, s.name""", fid);
        add(out, "OFFER_WITHOUT_SCHEDULE", 4, Severity.MEDIA, "Disciplinas ofertadas sem dia e horário", "Definir dia e horário da aula", """
                select k.code || ' — ' || s.name, '/academico/turmas/' || k.id from class_subjects cs join classes k on k.id = cs.class_id
                join academic_terms t on t.id = k.term_id join subjects s on s.id = cs.subject_id
                where cs.faculty_id = ? and cs.weekday is null and t.status <> 'CLOSED' order by k.code, s.name""", fid);
        add(out, "TEACHER_WITHOUT_ACCESS", 4, Severity.MEDIA, "Professores com aulas, mas sem acesso ao portal", "Gerar o acesso do professor", """
                select p.full_name, '/academico/professores' from teachers p
                where p.faculty_id = ? and p.status = 'ACTIVE' and p.user_id is null
                  and exists (select 1 from class_subjects cs where cs.teacher_id = p.id) order by p.full_name""", fid);
        add(out, "CLASS_WITHOUT_STUDENTS", 5, Severity.MEDIA, "Turmas sem alunos", "Incluir os alunos do curso na turma", """
                select k.code || ' (' || t.code || ')', '/academico/turmas/' || k.id from classes k join academic_terms t on t.id = k.term_id
                where k.faculty_id = ? and k.active and t.status <> 'CLOSED' and not exists (select 1 from class_students x where x.class_id = k.id)
                order by t.code, k.code""", fid);
        add(out, "STUDENT_WITHOUT_COURSE", 5, Severity.ALTA, "Alunos sem curso", "Editar o cadastro e informar o curso", """
                select s.registration || ' — ' || coalesce(s.social_name, s.full_name), '/academico/alunos' from students s
                where s.faculty_id = ? and s.status = 'ACTIVE' and s.course_id is null order by s.full_name""", fid);
        add(out, "STUDENT_WITHOUT_CLASS", 5, Severity.ALTA, "Alunos ativos sem turma no período", "Incluir o aluno em uma turma do curso dele", """
                select s.registration || ' — ' || coalesce(s.social_name, s.full_name), '/academico/alunos' from students s
                where s.faculty_id = ? and s.status = 'ACTIVE' and not exists (
                  select 1 from class_students x join classes k on k.id = x.class_id join academic_terms t on t.id = k.term_id
                  where x.student_id = s.id and t.status <> 'CLOSED') order by s.full_name""", fid);
        add(out, "STUDENT_WITHOUT_ACCESS", 5, Severity.BAIXA, "Alunos ativos sem acesso ao portal", "Gerar o acesso do aluno (usuário = matrícula)", """
                select s.registration || ' — ' || coalesce(s.social_name, s.full_name), '/academico/alunos' from students s
                where s.faculty_id = ? and s.status = 'ACTIVE' and s.user_id is null order by s.full_name""", fid);
        return out;
    }

    public long total(UUID fid) {
        return list(fid).stream().mapToLong(Pending::count).sum();
    }

    private void add(List<Pending> out, String key, int step, Severity sev, String title, String action, String sql, UUID fid) {
        long total = count("select count(*) from (" + sql.replaceFirst("order by[^)]*$", "") + ") q", fid);
        if (total == 0) return;
        List<Item> items = jdbc.query(sql + " limit " + LIMIT, (rs, i) -> new Item(rs.getString(1), rs.getString(2)), fid);
        out.add(new Pending(key, step, sev, title, action, items.isEmpty() ? null : items.get(0).link(), total, items));
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
