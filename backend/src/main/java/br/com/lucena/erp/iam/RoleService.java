package br.com.lucena.erp.iam;

import br.com.lucena.erp.audit.AuditService;
import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.iam.IamDtos.*;
import br.com.lucena.erp.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AccessGuard guard;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<RoleView> list(UUID facultyId) {
        return roles.findByFacultyIdOrderByNameAsc(facultyId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionView> permissionCatalog() {
        return permissions.findByPlatformOnlyFalseOrderByModuleAscCodeAsc().stream()
                .map(p -> new PermissionView(p.getCode(), p.getModule(), p.getDescription())).toList();
    }

    @Transactional
    public RoleView create(UUID facultyId, RoleRequest req, AuthPrincipal actor, ClientInfo client) {
        if (roles.existsByFacultyIdAndCode(facultyId, req.code())) {
            throw ApiException.conflict("ROLE_CODE_IN_USE", "Já existe um cargo com este código");
        }
        Role r = new Role();
        r.setFacultyId(facultyId);
        r.setCode(req.code());
        r.setName(req.name().trim());
        r.setDescription(req.description());
        r.setSystemRole(false);
        r.setPermissions(resolve(req.permissions(), actor));
        roles.save(r);
        audit.record(actor, facultyId, "ROLE_CREATED", "ROLE", r.getId(), Map.of("code", r.getCode(), "permissions", codes(r)), client);
        return view(r);
    }

    @Transactional
    public RoleView update(UUID facultyId, UUID id, RoleRequest req, AuthPrincipal actor, ClientInfo client) {
        Role r = find(facultyId, id);
        if (!r.isSystemRole() && !r.getCode().equals(req.code()) && roles.existsByFacultyIdAndCode(facultyId, req.code())) {
            throw ApiException.conflict("ROLE_CODE_IN_USE", "Já existe um cargo com este código");
        }
        guard.ensureCanGrant(actor, List.of(r));
        List<String> before = codes(r);
        if (!r.isSystemRole()) r.setCode(req.code());
        r.setName(req.name().trim());
        r.setDescription(req.description());
        r.setPermissions(resolve(req.permissions(), actor));
        audit.record(actor, facultyId, "ROLE_PERMISSIONS_CHANGED", "ROLE", r.getId(), Map.of("before", before, "after", codes(r)), client);
        return view(r);
    }

    @Transactional
    public void delete(UUID facultyId, UUID id, AuthPrincipal actor, ClientInfo client) {
        Role r = find(facultyId, id);
        if (r.isSystemRole()) throw ApiException.badRequest("SYSTEM_ROLE", "Cargos padrão do sistema não podem ser excluídos");
        if (roles.countAssignments(id) > 0) throw ApiException.conflict("ROLE_IN_USE", "Cargo vinculado a usuários");
        roles.delete(r);
        audit.record(actor, facultyId, "ROLE_DELETED", "ROLE", id, Map.of("code", r.getCode()), client);
    }

    private Set<Permission> resolve(Set<String> codes, AuthPrincipal actor) {
        List<Permission> found = permissions.findByCodeIn(codes);
        if (found.size() != codes.size() || found.stream().anyMatch(Permission::isPlatformOnly)) {
            throw ApiException.badRequest("INVALID_PERMISSION", "Permissão inexistente ou não permitida");
        }
        guard.ensureCanGrantCodes(actor, codes);
        return new HashSet<>(found);
    }

    private Role find(UUID facultyId, UUID id) {
        return roles.findByIdAndFacultyId(id, facultyId).orElseThrow(() -> ApiException.notFound("Cargo"));
    }

    private static List<String> codes(Role r) {
        return r.getPermissions().stream().map(Permission::getCode).sorted().toList();
    }

    private RoleView view(Role r) {
        return new RoleView(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isSystemRole(), codes(r),
                r.getId() == null ? 0 : roles.countAssignments(r.getId()));
    }
}
