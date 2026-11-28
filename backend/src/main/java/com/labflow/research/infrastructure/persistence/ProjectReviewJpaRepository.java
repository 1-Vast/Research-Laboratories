package com.labflow.research.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectReviewJpaRepository extends JpaRepository<ProjectReviewJpaEntity, Long> {

    List<ProjectReviewJpaEntity> findByProjectIdOrderByReviewedAtAsc(Long projectId);
}
