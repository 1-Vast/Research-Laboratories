package com.labflow.research.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 周报状态机单元测试：同周唯一、提交后只读、禁止自审。 */
@DisplayName("周报状态机")
class ReportTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");
    private static final Long PROJECT = 100L;
    private static final Long AUTHOR = 31L;
    private static final Long LEADER = 30L;

    private Report report() {
        return Report.create(PROJECT, AUTHOR, LocalDate.of(2026, 11, 23), "完成数据清洗", "无", "训练模型", NOW);
    }

    @Test
    @DisplayName("周起始日归一到周一")
    void weekStartNormalizedToMonday() {
        Report midWeek = Report.create(PROJECT, AUTHOR, LocalDate.of(2026, 11, 25), "工作", null, null, NOW);
        assertThat(midWeek.weekStart()).isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    @DisplayName("新周报为草稿")
    void newReportIsDraft() {
        Report report = report();
        assertThat(report.status()).isEqualTo(ReportStatus.DRAFT);
        assertThat(report.version()).isZero();
        assertThat(report.isAuthor(AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("草稿 → 提交 → 通过")
    void submitThenApprove() {
        Report report = report();
        report.submit(NOW);
        assertThat(report.status()).isEqualTo(ReportStatus.SUBMITTED);

        report.review(LEADER, ReviewDecision.APPROVE, "记录完整", NOW);
        assertThat(report.status()).isEqualTo(ReportStatus.APPROVED);
        assertThat(report.reviewerId()).isEqualTo(LEADER);
    }

    @Test
    @DisplayName("作者审核自己的周报被拒绝")
    void cannotSelfReview() {
        Report report = report();
        report.submit(NOW);
        assertThatThrownBy(() -> report.review(AUTHOR, ReviewDecision.APPROVE, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.SELF_REVIEW_FORBIDDEN);
    }

    @Test
    @DisplayName("提交后正文只读")
    void submittedIsReadOnly() {
        Report report = report();
        report.submit(NOW);
        assertThatThrownBy(() -> report.update("偷偷改内容", null, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        assertThat(report.isLocked()).isTrue();
    }

    @Test
    @DisplayName("退回后可以修改并重新提交")
    void returnedCanEditAndResubmit() {
        Report report = report();
        report.submit(NOW);
        report.review(LEADER, ReviewDecision.REJECT, "缺少结果", NOW);
        assertThat(report.status()).isEqualTo(ReportStatus.RETURNED);

        report.update("完成数据清洗与结果分析", null, null, NOW);
        report.submit(NOW);
        assertThat(report.status()).isEqualTo(ReportStatus.SUBMITTED);
    }

    @Test
    @DisplayName("退回必须填意见")
    void returnRequiresNote() {
        Report report = report();
        report.submit(NOW);
        assertThatThrownBy(() -> report.review(LEADER, ReviewDecision.REJECT, "", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("提交时本周工作不能为空")
    void workRequiredOnSubmit() {
        Report empty = Report.create(PROJECT, AUTHOR, LocalDate.of(2026, 11, 23), "  ", null, null, NOW);
        assertThatThrownBy(() -> empty.submit(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("未提交的周报不能审核")
    void cannotReviewDraft() {
        assertThatThrownBy(() -> report().review(LEADER, ReviewDecision.APPROVE, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("只有草稿可以删除")
    void onlyDraftDeletable() {
        Report report = report();
        report.assertDeletable();
        report.submit(NOW);
        assertThatThrownBy(report::assertDeletable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.REPORT_NOT_DELETABLE);
    }

    @Test
    @DisplayName("周起始日为空被拒绝")
    void weekStartRequired() {
        assertThatThrownBy(() -> Report.create(PROJECT, AUTHOR, null, "工作", null, null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
