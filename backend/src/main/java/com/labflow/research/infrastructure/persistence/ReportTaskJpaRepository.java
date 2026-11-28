package com.labflow.research.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportTaskJpaRepository extends JpaRepository<ReportTaskJpaEntity, ReportTaskId> {

    List<ReportTaskJpaEntity> findByReportId(Long reportId);

    void deleteByReportId(Long reportId);
}
