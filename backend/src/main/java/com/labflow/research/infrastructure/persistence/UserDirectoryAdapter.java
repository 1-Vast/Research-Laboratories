package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.repository.UserDirectory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 用户存在性只读适配器；同样避免为身份模块的表造实体。 */
@Repository
@Transactional(readOnly = true)
public class UserDirectoryAdapter implements UserDirectory {

    private final JdbcTemplate jdbc;

    public UserDirectoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "select count(*) from users where id = ? and enabled = true", Integer.class, userId);
        return count != null && count > 0;
    }
}
