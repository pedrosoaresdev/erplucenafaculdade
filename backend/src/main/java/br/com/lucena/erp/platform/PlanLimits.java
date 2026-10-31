package br.com.lucena.erp.platform;

import br.com.lucena.erp.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Limites do plano contratado pela faculdade (alunos e usuários). */
@Component
@RequiredArgsConstructor
public class PlanLimits {
    private final JdbcTemplate jdbc;

    public void ensureCanAddStudent(UUID facultyId) {
        check(facultyId, "max_students", "select count(*) from students where faculty_id = ?", "alunos");
    }

    public void ensureCanAddUser(UUID facultyId) {
        check(facultyId, "max_users", "select count(*) from users where faculty_id = ?", "usuários");
    }

    private void check(UUID facultyId, String column, String countSql, String label) {
        Integer limit = jdbc.query("select p." + column + " from faculties f join plans p on p.id = f.plan_id where f.id = ?",
                rs -> rs.next() ? (Integer) rs.getObject(1) : null, facultyId);
        if (limit == null) return;
        Long current = jdbc.queryForObject(countSql, Long.class, facultyId);
        if (current != null && current >= limit) {
            throw ApiException.conflict("PLAN_LIMIT_REACHED",
                    "Limite de " + limit + " " + label + " do plano contratado atingido. Contate a administração da plataforma");
        }
    }
}
