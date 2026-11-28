package com.labflow.research.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberJpaRepository extends JpaRepository<ProjectMemberJpaEntity, ProjectMemberId> {

    List<ProjectMemberJpaEntity> findByProjectIdOrderByJoinedAtAsc(Long projectId);

    boolean existsByProjectIdAndUserIdAndActiveTrue(Long projectId, Long userId);
}
