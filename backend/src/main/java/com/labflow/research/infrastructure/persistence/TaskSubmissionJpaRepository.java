package com.labflow.research.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskSubmissionJpaRepository extends JpaRepository<TaskSubmissionJpaEntity, Long> {

    List<TaskSubmissionJpaEntity> findByTaskIdOrderBySubmissionNoAsc(Long taskId);

    @Query("select coalesce(max(s.submissionNo), 0) from TaskSubmissionJpaEntity s where s.taskId = :taskId")
    int maxSubmissionNo(@Param("taskId") Long taskId);
}
