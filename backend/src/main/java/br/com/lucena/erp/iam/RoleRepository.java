package br.com.lucena.erp.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    @Query("select r from Role r where r.facultyId is null order by r.name")
    List<Role> findTemplates();

    List<Role> findByFacultyIdOrderByNameAsc(UUID facultyId);

    Optional<Role> findByIdAndFacultyId(UUID id, UUID facultyId);

    Optional<Role> findByFacultyIdAndCode(UUID facultyId, String code);

    boolean existsByFacultyIdAndCode(UUID facultyId, String code);

    @Query(value = "select count(*) from user_roles where role_id = :roleId", nativeQuery = true)
    long countAssignments(@Param("roleId") UUID roleId);
}
