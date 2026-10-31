package br.com.lucena.erp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {
    Optional<Teacher> findByIdAndFacultyId(UUID id, UUID facultyId);

    boolean existsByFacultyIdAndCpf(UUID facultyId, String cpf);

    @Query("""
            select t from Teacher t where t.facultyId = :facultyId
              and (:status is null or t.status = :status)
              and (:q is null or lower(t.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(coalesce(t.email, '')) like lower(concat('%', cast(:q as string), '%')) or t.cpf = :q)
            """)
    Page<Teacher> search(@Param("facultyId") UUID facultyId, @Param("q") String q, @Param("status") Teacher.Status status, Pageable pageable);
}
