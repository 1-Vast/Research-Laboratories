package com.labflow.research.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

/** report_tasks 的复合主键（report_id + task_id）。 */
public class ReportTaskId implements Serializable {

    private Long reportId;
    private Long taskId;

    public ReportTaskId() {
    }

    public ReportTaskId(Long reportId, Long taskId) {
        this.reportId = reportId;
        this.taskId = taskId;
    }

    public Long getReportId() {
        return reportId;
    }

    public Long getTaskId() {
        return taskId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReportTaskId that)) {
            return false;
        }
        return Objects.equals(reportId, that.reportId) && Objects.equals(taskId, that.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportId, taskId);
    }
}
