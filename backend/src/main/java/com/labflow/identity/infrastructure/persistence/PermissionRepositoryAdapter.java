package com.labflow.identity.infrastructure.persistence;

import com.labflow.identity.domain.Permission;
import com.labflow.identity.domain.repository.PermissionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
@Transactional(readOnly = true)
public class PermissionRepositoryAdapter implements PermissionRepository {

    private final PermissionJpaRepository permissions;

    public PermissionRepositoryAdapter(PermissionJpaRepository permissions) {
        this.permissions = permissions;
    }

    @Override
    public List<Permission> findAll() {
        return permissions.findAll().stream()
                .map(IdentityMapper::toDomain)
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
    }

    @Override
    public List<Permission> findByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return permissions.findByCodeIn(codes).stream().map(IdentityMapper::toDomain).toList();
    }
}
