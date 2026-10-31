package br.com.lucena.erp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByIdAndFacultyId(UUID id, UUID facultyId);

    boolean existsByFacultyIdAndCpf(UUID facultyId, String cpf);

    boolean existsByFacultyIdAndRegistrationIgnoreCase(UUID facultyId, String registration);

    long countByFacultyId(UUID facultyId);

    @Query("""
            select s from Student s where s.facultyId = :facultyId
              and (:status is null or s.status = :status)
              and (:courseId is null or s.courseId = :courseId)
              and (:q is null or lower(s.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(coalesce(s.socialName, '')) like lower(concat('%', cast(:q as string), '%'))
                   or lower(s.registration) like lower(concat('%', cast(:q as string), '%'))
                   or s.cpf = :q)
            """)
    Page<Student> search(@Param("facultyId") UUID facultyId, @Param("q") String q, @Param("status") Student.Status status,
                         @Param("courseId") UUID courseId, Pageable pageable);
}
