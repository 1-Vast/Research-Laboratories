package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.repository.LabScopeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 实验室作用域只读适配器。
 *
 * <p>这里刻意使用 {@link JdbcTemplate} 而不是 JPA 实体：lab_members 属于身份与组织模块的表，
 * 科研模块只读它的两个判断结果。若为它造实体，等于让科研模块依赖别人的持久化模型，
 * 反而破坏模块边界。</p>
 */
@Repository
@Transactional(readOnly = true)
public class LabScopeRepositoryAdapter implements LabScopeRepository {

    private static final String PI = "PI";

    private final JdbcTemplate jdbc;

    public LabScopeRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isMember(Long labId, Long userId) {
        if (labId == null || userId == null) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "select count(*) from lab_members where lab_id = ? and user_id = ? and active = true",
                Integer.class, labId, userId);
        return count != null && count > 0;
    }

    @Override
    public boolean isPi(Long labId, Long userId) {
        if (labId == null || userId == null) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "select count(*) from lab_members where lab_id = ? and user_id = ? and active = true "
                        + "and member_type = ?",
                Integer.class, labId, userId, PI);
        return count != null && count > 0;
    }

    @Override
    public List<Long> findPiLabIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return jdbc.queryForList(
                "select lab_id from lab_members where user_id = ? and active = true and member_type = ?",
                Long.class, userId, PI);
    }

    @Override
    public List<Long> findMemberLabIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return jdbc.queryForList(
                "select lab_id from lab_members where user_id = ? and active = true",
                Long.class, userId);
    }

    @Override
    public boolean existsEnabled(Long labId) {
        if (labId == null) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "select count(*) from labs where id = ? and enabled = true", Integer.class, labId);
        return count != null && count > 0;
    }
}
