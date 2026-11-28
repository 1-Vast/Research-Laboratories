package com.labflow.research.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReportTaskJpaRepository extends JpaRepository<ReportTaskJpaEntity, ReportTaskId> {

    List<ReportTaskJpaEntity> findByReportId(Long reportId);

    /** 列表页一次取回整页的关联，避免逐条查询造成 N+1。 */
    List<ReportTaskJpaEntity> findByReportIdIn(Collection<Long> reportIds);

    void deleteByReportId(Long reportId);
}
