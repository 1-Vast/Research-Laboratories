package com.labflow.identity.domain.repository;

import com.labflow.identity.domain.Role;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findById(Long id);

    Optional<Role> findByCode(String code);

    List<Role> findByCodes(Collection<String> codes);

    List<Role> findAll();

    boolean existsByCode(String code);

    Role save(Role role);

    void delete(Role role);

    PageResult<Role> search(String keyword, int page, int size, SortSpec sort);

    long countAssignedUsers(Long roleId);
}
