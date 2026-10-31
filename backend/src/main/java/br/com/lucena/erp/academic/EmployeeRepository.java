package br.com.lucena.erp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByIdAndFacultyId(UUID id, UUID facultyId);

    boolean existsByFacultyIdAndCpf(UUID facultyId, String cpf);

    @Query("""
            select e from Employee e where e.facultyId = :facultyId
              and (:status is null or e.status = :status)
              and (:q is null or lower(e.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(coalesce(e.department, '')) like lower(concat('%', cast(:q as string), '%')) or e.cpf = :q)
            """)
    Page<Employee> search(@Param("facultyId") UUID facultyId, @Param("q") String q, @Param("status") Employee.Status status, Pageable pageable);
}
