package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.repository.LabDirectoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 实验室目录只读适配器；与作用域适配器同源，避免为组织表造实体。 */
@Repository
@Transactional(readOnly = true)
public class LabDirectoryRepositoryAdapter implements LabDirectoryRepository {

    private final JdbcTemplate jdbc;

    public LabDirectoryRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LabSummary> findMyLabs(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return jdbc.query(
                """
                select l.id, l.code, l.name
                from labs l
                join lab_members m on m.lab_id = l.id
                where m.user_id = ? and m.active = true and l.enabled = true
                order by l.code
                """,
                (rs, rowNum) -> new LabSummary(rs.getLong("id"), rs.getString("code"), rs.getString("name")),
                userId);
    }
}
