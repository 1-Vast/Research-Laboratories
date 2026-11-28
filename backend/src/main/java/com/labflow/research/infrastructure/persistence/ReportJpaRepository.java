package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ReportJpaRepository extends JpaRepository<ReportJpaEntity, Long> {

    @Query("""
            select r from ReportJpaEntity r
            where (:projectId is null or r.projectId = :projectId)
              and (:authorId is null or r.authorId = :authorId)
              and (:status is null or r.status = :status)
            """)
    Page<ReportJpaEntity> search(@Param("projectId") Long projectId,
                                 @Param("authorId") Long authorId,
                                 @Param("status") ReportStatus status,
                                 Pageable pageable);

    boolean existsByProjectIdAndAuthorIdAndWeekStart(Long projectId, Long authorId, LocalDate weekStart);
}
