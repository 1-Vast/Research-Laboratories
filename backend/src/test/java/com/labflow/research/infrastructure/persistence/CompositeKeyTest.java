package com.labflow.research.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 复合主键的相等性：JPA 用它们做一级缓存键，写错会导致重复插入或缓存错乱。 */
class CompositeKeyTest {

    @Test
    @DisplayName("ProjectMemberId 按 projectId + userId 判等")
    void projectMemberIdEquality() {
        ProjectMemberId a = new ProjectMemberId(1L, 2L);
        ProjectMemberId b = new ProjectMemberId(1L, 2L);
        ProjectMemberId other = new ProjectMemberId(1L, 3L);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(other);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("1-2");
        assertThat(a.getProjectId()).isEqualTo(1L);
        assertThat(a.getUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("ReportTaskId 按 reportId + taskId 判等")
    void reportTaskIdEquality() {
        ReportTaskId a = new ReportTaskId(5L, 6L);
        ReportTaskId b = new ReportTaskId(5L, 6L);
        ReportTaskId other = new ReportTaskId(6L, 5L);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(other);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("5-6");
        assertThat(a.getReportId()).isEqualTo(5L);
        assertThat(a.getTaskId()).isEqualTo(6L);
    }
}
