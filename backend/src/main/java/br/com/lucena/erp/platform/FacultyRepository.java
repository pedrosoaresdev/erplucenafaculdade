package br.com.lucena.erp.platform;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FacultyRepository extends JpaRepository<Faculty, UUID> {
    Optional<Faculty> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByCnpj(String cnpj);

    long countByStatus(Faculty.Status status);

    @Query("""
            select f from Faculty f
            where (:status is null or f.status = :status)
              and (:q is null or lower(f.name) like lower(concat('%', cast(:q as string), '%'))
                   or f.cnpj like concat('%', cast(:q as string), '%')
                   or lower(f.slug) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Faculty> search(@Param("q") String q, @Param("status") Faculty.Status status, Pageable pageable);
}
