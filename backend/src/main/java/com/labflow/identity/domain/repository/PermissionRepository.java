package com.labflow.identity.domain.repository;

import com.labflow.identity.domain.Permission;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository {

    List<Permission> findAll();

    List<Permission> findByCodes(Collection<String> codes);
}
