package com.labflow.research.interfaces.rest;

import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectMember;
import com.labflow.research.domain.ProjectReview;
import com.labflow.research.domain.ProjectStatus;
import com.labflow.research.domain.ReviewDecision;
import com.labflow.research.interfaces.rest.vo.ProgressVo;
import com.labflow.research.interfaces.rest.vo.ProjectMemberVo;
import com.labflow.research.interfaces.rest.vo.ProjectReviewVo;
import com.labflow.research.interfaces.rest.vo.ProjectVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 返回体映射：字段一旦漏映射，前端会静默拿到 null，因此逐字段断言。 */
class ResearchWebMapperTest {

    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

    @Test
    @DisplayName("项目返回体保留状态、版本与计划日期")
    void mapsProject() {
        Project project = Project.restore(7L, 1L, 2L, "感知项目", "目标", ProjectStatus.ACTIVE,
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 31), null, 3, NOW, NOW);

        ProjectVo vo = ResearchWebMapper.toProjectVo(project);

        assertThat(vo.id()).isEqualTo(7L);
        assertThat(vo.labId()).isEqualTo(1L);
        assertThat(vo.leaderId()).isEqualTo(2L);
        assertThat(vo.name()).isEqualTo("感知项目");
        assertThat(vo.status()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(vo.plannedEnd()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(vo.version()).isEqualTo(3);
    }

    @Test
    @DisplayName("进度百分比四舍五入，分母为 0 时返回 0")
    void mapsProgress() {
        assertThat(ResearchWebMapper.toProgressVo(4, 1).percent()).isEqualTo(25);
        assertThat(ResearchWebMapper.toProgressVo(3, 2).percent()).isEqualTo(67);
        assertThat(ResearchWebMapper.toProgressVo(0, 0)).isEqualTo(new ProgressVo(0, 0, 0));
    }

    @Test
    @DisplayName("成员与审批轨迹返回体字段完整")
    void mapsMemberAndReview() {
        ProjectMemberVo member = ResearchWebMapper.toMemberVo(
                ProjectMember.of(7L, 9L, ProjectMember.Type.LEADER, NOW));
        assertThat(member.userId()).isEqualTo(9L);
        assertThat(member.memberType()).isEqualTo(ProjectMember.Type.LEADER);
        assertThat(member.active()).isTrue();

        ProjectReviewVo review = ResearchWebMapper.toReviewVo(ProjectReview.restore(1L, 7L,
                ProjectReview.Type.INIT, 3L, ReviewDecision.REJECT, "目标不聚焦", NOW));
        assertThat(review.projectId()).isEqualTo(7L);
        assertThat(review.reviewType()).isEqualTo(ProjectReview.Type.INIT);
        assertThat(review.decision()).isEqualTo(ReviewDecision.REJECT);
        assertThat(review.reason()).isEqualTo("目标不聚焦");
        assertThat(review.reviewedAt()).isEqualTo(NOW);
    }
}
