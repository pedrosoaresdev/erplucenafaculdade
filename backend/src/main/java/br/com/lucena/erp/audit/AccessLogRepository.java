package br.com.lucena.erp.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {
    Page<AccessLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AccessLog> findByFacultyIdOrderByCreatedAtDesc(UUID facultyId, Pageable pageable);

    Page<AccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countBySuccessAndEventAndCreatedAtAfter(boolean success, String event, Instant after);

    @Query("select count(a) from AccessLog a where a.success = false and a.createdAt > :after")
    long countFailuresSince(@Param("after") Instant after);
}
