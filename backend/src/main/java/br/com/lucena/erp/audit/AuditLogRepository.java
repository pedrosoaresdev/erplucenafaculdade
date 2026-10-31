package br.com.lucena.erp.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query("""
            select a from AuditLog a
            where ((:facultyId is null and :platformScope = true) or a.facultyId = :facultyId)
              and (:action is null or a.action = :action)
              and (:entityType is null or a.entityType = :entityType)
            order by a.createdAt desc
            """)
    Page<AuditLog> search(@Param("facultyId") UUID facultyId, @Param("platformScope") boolean platformScope,
                          @Param("action") String action, @Param("entityType") String entityType, Pageable pageable);

    long countByEntityTypeAndEntityId(String entityType, String entityId);

    long countByAction(String action);
}
