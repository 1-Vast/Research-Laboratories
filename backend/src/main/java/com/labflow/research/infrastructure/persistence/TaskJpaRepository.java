package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskJpaRepository extends JpaRepository<TaskJpaEntity, Long> {

    @Query("""
            select t from TaskJpaEntity t
            where (:projectId is null or t.projectId = :projectId)
              and (:status is null or t.status = :status)
              and (:assigneeId is null or t.assigneeId = :assigneeId)
              and (:pattern is null or lower(t.title) like :pattern)
            """)
    Page<TaskJpaEntity> search(@Param("projectId") Long projectId,
                               @Param("status") TaskStatus status,
                               @Param("assigneeId") Long assigneeId,
                               @Param("pattern") String pattern,
                               Pageable pageable);

    @Query("select count(t) from TaskJpaEntity t where t.projectId = :projectId and t.status <> :excluded")
    long countExcluding(@Param("projectId") Long projectId, @Param("excluded") TaskStatus excluded);

    @Query("select count(t) from TaskJpaEntity t where t.projectId = :projectId and t.status = :status")
    long countByStatus(@Param("projectId") Long projectId, @Param("status") TaskStatus status);

    @Query("select count(t) from TaskJpaEntity t where t.projectId = :projectId and t.status not in (:done, :canceled)")
    long countOpen(@Param("projectId") Long projectId, @Param("done") TaskStatus done,
                   @Param("canceled") TaskStatus canceled);
}
