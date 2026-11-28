package com.labflow.research.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** report_tasks 表的 JPA 映射（周报与任务多对多）。 */
@Entity
@Table(name = "report_tasks")
@IdClass(ReportTaskId.class)
public class ReportTaskJpaEntity {

    @Id
    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Id
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    protected ReportTaskJpaEntity() {
    }

    public static ReportTaskJpaEntity of(Long reportId, Long taskId) {
        ReportTaskJpaEntity entity = new ReportTaskJpaEntity();
        entity.reportId = reportId;
        entity.taskId = taskId;
        return entity;
    }

    public Long getReportId() {
        return reportId;
    }

    public Long getTaskId() {
        return taskId;
    }
}
