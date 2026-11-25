package com.labflow.identity.domain.repository;

import com.labflow.identity.domain.User;
import com.labflow.shared.api.PageResult;

import java.util.Optional;

/**
 * 用户仓储端口。领域层只声明需要的能力，具体实现（JPA / MyBatis / 其他）在基础设施层。
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    User save(User user);

    PageResult<User> search(String keyword, int page, int size);

    /** 统计启用状态且拥有指定角色的用户数（用于“最后一个管理员”保护）。 */
    long countEnabledUsersWithRole(String roleCode);

    /** 统计除指定用户外，仍启用且拥有指定角色的用户数。 */
    long countOtherEnabledUsersWithRole(Long excludedUserId, String roleCode);
}
