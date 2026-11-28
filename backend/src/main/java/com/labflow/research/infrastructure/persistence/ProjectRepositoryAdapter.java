package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectStatus;
import com.labflow.research.domain.repository.ProjectRepository;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class ProjectRepositoryAdapter implements ProjectRepository {

    /** JPQL 不支持 in () 空集合，用不可能命中的占位值代替。 */
    private static final List<Long> EMPTY_SCOPE = List.of(-1L);

    private final ProjectJpaRepository projects;

    public ProjectRepositoryAdapter(ProjectJpaRepository projects) {
        this.projects = projects;
    }

    @Override
    @Transactional
    public Project save(Project project) {
        ProjectJpaEntity entity = project.id() == null
                ? new ProjectJpaEntity()
                : projects.findById(project.id())
                        .orElseThrow(() -> new IllegalStateException("项目不存在: " + project.id()));
        ResearchMapper.copyToEntity(project, entity);
        return ResearchMapper.toDomain(projects.save(entity));
    }

    @Override
    public Optional<Project> findById(Long id) {
        return projects.findById(id).map(ResearchMapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(Project project) {
        if (project.id() != null) {
            projects.deleteById(project.id());
        }
    }

    @Override
    public PageResult<Project> search(List<Long> piLabIds, Long memberUserId, Long labId, ProjectStatus status,
                                      String keyword, int page, int size, SortSpec sort) {
        List<Long> scope = piLabIds == null || piLabIds.isEmpty() ? EMPTY_SCOPE : piLabIds;
        String pattern = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Sort.Direction direction = sort.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(direction, sort.field()));
        Page<ProjectJpaEntity> result = projects.searchScoped(scope, memberUserId, labId, status, pattern, pageRequest);
        return PageResult.of(result.getContent().stream().map(ResearchMapper::toDomain).toList(),
                result.getTotalElements(), page, size);
    }
}
