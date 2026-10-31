package br.com.lucena.erp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {
    List<AttendanceSession> findByClassSubjectIdOrderBySessionDateDesc(UUID classSubjectId);

    List<AttendanceSession> findByClassSubjectIdIn(Collection<UUID> classSubjectIds);

    Optional<AttendanceSession> findByIdAndClassSubjectId(UUID id, UUID classSubjectId);

    boolean existsByClassSubjectIdAndSessionDate(UUID classSubjectId, LocalDate date);
}
