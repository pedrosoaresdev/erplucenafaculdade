package br.com.lucena.erp.iam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findByPlatformOnlyFalseOrderByModuleAscCodeAsc();

    List<Permission> findByCodeIn(Collection<String> codes);
}
