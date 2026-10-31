package br.com.lucena.erp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    List<Assessment> findByClassSubjectIdOrderByDueDateAscCreatedAtAsc(UUID classSubjectId);

    List<Assessment> findByClassSubjectIdIn(Collection<UUID> classSubjectIds);

    Optional<Assessment> findByIdAndClassSubjectId(UUID id, UUID classSubjectId);
}
