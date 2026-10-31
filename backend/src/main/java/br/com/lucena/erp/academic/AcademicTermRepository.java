package br.com.lucena.erp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, UUID> {
    Optional<AcademicTerm> findByIdAndFacultyId(UUID id, UUID facultyId);

    boolean existsByFacultyIdAndCode(UUID facultyId, String code);

    List<AcademicTerm> findByFacultyIdOrderByCodeDesc(UUID facultyId);
}
