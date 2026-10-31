package br.com.lucena.erp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Optional<Course> findByIdAndFacultyId(UUID id, UUID facultyId);

    boolean existsByFacultyIdAndCodeIgnoreCase(UUID facultyId, String code);

    long countByFacultyId(UUID facultyId);

    @Query("""
            select c from Course c where c.facultyId = :facultyId
              and (:active is null or c.active = :active)
              and (:q is null or lower(c.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(c.code) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Course> search(@Param("facultyId") UUID facultyId, @Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
