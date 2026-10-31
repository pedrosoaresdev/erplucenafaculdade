package br.com.lucena.erp.iam;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u where u.facultyId = :facultyId and lower(u.username) = lower(:username)")
    Optional<User> findInFaculty(@Param("facultyId") UUID facultyId, @Param("username") String username);

    @Query("select u from User u where u.facultyId is null and lower(u.username) = lower(:username)")
    Optional<User> findPlatformUser(@Param("username") String username);

    Optional<User> findByIdAndFacultyId(UUID id, UUID facultyId);

    boolean existsByPlatformAdminTrue();

    long countByFacultyId(UUID facultyId);

    @Query("""
            select u from User u where u.facultyId = :facultyId
              and (:status is null or u.status = :status)
              and (:q is null or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.username) like lower(concat('%', cast(:q as string), '%'))
                   or lower(u.email) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<User> search(@Param("facultyId") UUID facultyId, @Param("q") String q, @Param("status") User.Status status, Pageable pageable);

    @Query("select count(distinct u) from User u join u.roles r where r.code = :roleCode and u.facultyId is not null")
    long countByRoleCode(@Param("roleCode") String roleCode);
}
