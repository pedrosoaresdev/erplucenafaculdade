package br.com.lucena.erp.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    Optional<SchoolClass> findByIdAndFacultyId(UUID id, UUID facultyId);

    boolean existsByFacultyIdAndTermIdAndCodeIgnoreCase(UUID facultyId, UUID termId, String code);

    boolean existsByCourseId(UUID courseId);

    boolean existsByTermId(UUID termId);

    @Query("""
            select c from SchoolClass c where c.facultyId = :facultyId
              and (:termId is null or c.termId = :termId)
              and (:courseId is null or c.courseId = :courseId)
            order by c.code
            """)
    List<SchoolClass> search(@Param("facultyId") UUID facultyId, @Param("termId") UUID termId, @Param("courseId") UUID courseId);
}
