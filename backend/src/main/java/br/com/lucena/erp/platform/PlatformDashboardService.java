package br.com.lucena.erp.platform;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** Indicadores globais da plataforma, calculados a partir do banco. */
@Service
@RequiredArgsConstructor
public class PlatformDashboardService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public record Dashboard(long faculties, long activeFaculties, long suspendedFaculties, long archivedFaculties,
                            long students, long teachers, long employees, long courses, long users,
                            long logins24h, long failedLogins24h, long activeSessions, long openTickets,
                            long securityEvents7d, List<Map<String, Object>> facultiesByUsage,
                            List<Map<String, Object>> loginsByDay) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Timestamp d1 = Timestamp.from(clock.instant().minus(Duration.ofDays(1)));
        Timestamp d7 = Timestamp.from(clock.instant().minus(Duration.ofDays(7)));
        Timestamp d14 = Timestamp.from(clock.instant().minus(Duration.ofDays(14)));
        return new Dashboard(
                count("select count(*) from faculties"),
                count("select count(*) from faculties where status = 'ACTIVE'"),
                count("select count(*) from faculties where status = 'SUSPENDED'"),
                count("select count(*) from faculties where status = 'ARCHIVED'"),
                count("select count(*) from students"),
                count("select count(*) from teachers"),
                count("select count(*) from employees"),
                count("select count(*) from courses"),
                count("select count(*) from users"),
                jdbc.queryForObject("select count(*) from access_logs where success and event in ('LOGIN_MFA','LOGIN_MFA_SETUP','LOGIN_RECOVERY_CODE') and created_at > ?", Long.class, d1),
                jdbc.queryForObject("select count(*) from access_logs where not success and created_at > ?", Long.class, d1),
                jdbc.queryForObject("select count(*) from user_sessions where revoked_at is null and expires_at > now()", Long.class),
                count("select count(*) from support_tickets where status in ('OPEN','IN_PROGRESS')"),
                jdbc.queryForObject("""
                        select count(*) from audit_logs where created_at > ? and action in
                        ('MFA_RESET_BY_ADMIN','MFA_DISABLED','MFA_ROTATED','RECOVERY_CODE_USED','PASSWORD_RESET_BY_ADMIN','USER_ROLES_CHANGED','ROLE_PERMISSIONS_CHANGED')
                        """, Long.class, d7),
                jdbc.queryForList("""
                        select f.name, f.status,
                          (select count(*) from users u where u.faculty_id = f.id) as users,
                          (select count(*) from students s where s.faculty_id = f.id) as students
                        from faculties f order by users desc, f.name limit 10
                        """),
                jdbc.queryForList("""
                        select to_char(date_trunc('day', created_at), 'YYYY-MM-DD') as day,
                               count(*) filter (where success) as success, count(*) filter (where not success) as failure
                        from access_logs where created_at > ? group by 1 order by 1
                        """, d14));
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }
}
