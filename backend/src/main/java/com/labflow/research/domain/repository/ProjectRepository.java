package com.labflow.research.domain.repository;

import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectStatus;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;

import java.util.List;
import java.util.Optional;

/** 项目聚合的仓储端口。 */
public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(Long id);

    void delete(Project project);

    /**
     * 按作用域分页查询：只返回“该用户是 PI 的实验室”或“该用户是项目成员”的项目。
     *
     * @param piLabIds     该用户担任 PI 的实验室 ID；无则传空列表
     * @param memberUserId 当前用户 ID，用于匹配项目成员关系
     */
    PageResult<Project> search(List<Long> piLabIds, Long memberUserId, Long labId, ProjectStatus status,
                               String keyword, int page, int size, SortSpec sort);
}
