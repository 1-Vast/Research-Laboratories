package com.labflow.identity.infrastructure.persistence;

import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.repository.RoleRepository;
import com.labflow.shared.api.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository roles;
    private final PermissionJpaRepository permissions;

    public RoleRepositoryAdapter(RoleJpaRepository roles, PermissionJpaRepository permissions) {
        this.roles = roles;
        this.permissions = permissions;
    }

    @Override
    public Optional<Role> findById(Long id) {
        return roles.findWithPermissionsById(id).map(IdentityMapper::toDomain);
    }

    @Override
    public Optional<Role> findByCode(String code) {
        return roles.findByCode(code).map(IdentityMapper::toDomain);
    }

    @Override
    public List<Role> findByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return roles.findAll().stream()
                .filter(role -> codes.contains(role.getCode()))
                .map(IdentityMapper::toDomain)
                .toList();
    }

    @Override
    public List<Role> findAll() {
        return roles.findAllWithPermissions().stream().map(IdentityMapper::toDomain).toList();
    }

    @Override
    public boolean existsByCode(String code) {
        return roles.existsByCode(code);
    }

    @Override
    @Transactional
    public Role save(Role role) {
        RoleJpaEntity entity = role.id() == null
                ? new RoleJpaEntity()
                : roles.findById(role.id()).orElseThrow(() -> new IllegalStateException("角色不存在: " + role.id()));
        IdentityMapper.copyToEntity(role, entity);
        var permissionEntities = new LinkedHashSet<PermissionJpaEntity>();
        role.permissions().forEach(permission -> {
            if (permission.id() != null) {
                permissionEntities.add(permissions.getReferenceById(permission.id()));
            }
        });
        entity.setPermissions(permissionEntities);
        return IdentityMapper.toDomain(roles.save(entity));
    }

    @Override
    @Transactional
    public void delete(Role role) {
        if (role.id() != null) {
            roles.deleteById(role.id());
        }
    }

    @Override
    public PageResult<Role> search(String keyword, int page, int size) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String pattern = normalized == null ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<RoleJpaEntity> result = roles.search(normalized, pattern, pageRequest);
        return PageResult.of(result.getContent().stream().map(IdentityMapper::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public long countAssignedUsers(Long roleId) {
        return roles.countAssignedUsers(roleId);
    }
}
