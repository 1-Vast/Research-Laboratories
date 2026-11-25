package com.labflow.identity.infrastructure.persistence;

import com.labflow.identity.domain.Role;
import com.labflow.identity.domain.User;
import com.labflow.identity.domain.repository.UserRepository;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository users;
    private final RoleJpaRepository roles;

    public UserRepositoryAdapter(UserJpaRepository users, RoleJpaRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Override
    public Optional<User> findById(Long id) {
        return users.findWithAuthoritiesById(id).map(IdentityMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return users.findWithAuthoritiesByUsername(username).map(IdentityMapper::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return users.existsByUsername(username);
    }

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity entity = user.id() == null
                ? new UserJpaEntity()
                : users.findById(user.id()).orElseThrow(() -> new IllegalStateException("用户不存在: " + user.id()));
        IdentityMapper.copyToEntity(user, entity);
        entity.setRoles(toRoleEntities(user.roles()));
        UserJpaEntity saved = users.save(entity);
        return IdentityMapper.toDomain(saved);
    }

    private java.util.Set<RoleJpaEntity> toRoleEntities(java.util.Set<Role> domainRoles) {
        java.util.Set<RoleJpaEntity> entities = new LinkedHashSet<>();
        for (Role role : domainRoles) {
            if (role.id() == null) {
                throw new IllegalStateException("角色必须先持久化: " + role.code());
            }
            entities.add(roles.getReferenceById(role.id()));
        }
        return entities;
    }

    @Override
    public PageResult<User> search(String keyword, int page, int size, SortSpec sort) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String pattern = normalized == null ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        Sort.Direction direction = sort.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(direction, sort.field()));
        Page<UserJpaEntity> result = users.search(normalized, pattern, pageRequest);
        return PageResult.of(result.getContent().stream().map(IdentityMapper::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public long countEnabledUsersWithRole(String roleCode) {
        return users.countEnabledUsersWithRole(roleCode);
    }

    @Override
    public long countOtherEnabledUsersWithRole(Long excludedUserId, String roleCode) {
        return users.countOtherEnabledUsersWithRole(excludedUserId, roleCode);
    }
}
