package br.com.lucena.erp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    Optional<Subject> findByIdAndFacultyId(UUID id, UUID facultyId);

    boolean existsByFacultyIdAndCodeIgnoreCase(UUID facultyId, String code);

    List<Subject> findByFacultyIdAndIdIn(UUID facultyId, Collection<UUID> ids);

    @Query("""
            select s from Subject s where s.facultyId = :facultyId
              and (:active is null or s.active = :active)
              and (:q is null or lower(s.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(s.code) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Subject> search(@Param("facultyId") UUID facultyId, @Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
