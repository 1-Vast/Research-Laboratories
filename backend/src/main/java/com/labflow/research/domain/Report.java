package com.labflow.research.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 进度周报聚合根。
 *
 * <p>状态机：DRAFT --submit--> SUBMITTED --approve--> APPROVED；SUBMITTED --return--> RETURNED --submit--> SUBMITTED。</p>
 *
 * <p>不变量：同项目 + 同作者 + 同一周唯一（数据库唯一约束兜底）；提交后正文只读；审核人不得是作者本人。</p>
 */
public class Report {

    private Long id;
    private Long projectId;
    private Long authorId;
    private LocalDate weekStart;
    private String work;
    private String blockers;
    private String nextPlan;
    private ReportStatus status;
    private Long reviewerId;
    private String reviewNote;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    private Report() {
    }

    public static Report create(Long projectId, Long authorId, LocalDate weekStart, String work,
                                String blockers, String nextPlan, Instant now) {
        Report report = new Report();
        report.projectId = projectId;
        report.authorId = authorId;
        report.weekStart = normalizeWeekStart(weekStart);
        report.work = work;
        report.blockers = blockers;
        report.nextPlan = nextPlan;
        report.status = ReportStatus.DRAFT;
        report.version = 0;
        report.createdAt = now;
        report.updatedAt = now;
        return report;
    }

    public static Report restore(Long id, Long projectId, Long authorId, LocalDate weekStart, String work,
                                 String blockers, String nextPlan, ReportStatus status, Long reviewerId,
                                 String reviewNote, int version, Instant createdAt, Instant updatedAt) {
        Report report = new Report();
        report.id = id;
        report.projectId = projectId;
        report.authorId = authorId;
        report.weekStart = weekStart;
        report.work = work;
        report.blockers = blockers;
        report.nextPlan = nextPlan;
        report.status = status;
        report.reviewerId = reviewerId;
        report.reviewNote = reviewNote;
        report.version = version;
        report.createdAt = createdAt;
        report.updatedAt = updatedAt;
        return report;
    }

    /** 草稿与退回状态可以编辑正文。 */
    public void update(String work, String blockers, String nextPlan, Instant now) {
        if (status != ReportStatus.DRAFT && status != ReportStatus.RETURNED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "只有草稿或退回的周报可以修改");
        }
        this.work = work;
        this.blockers = blockers;
        this.nextPlan = nextPlan;
        touch(now);
    }

    /** DRAFT / RETURNED → SUBMITTED。 */
    public void submit(Instant now) {
        if (status != ReportStatus.DRAFT && status != ReportStatus.RETURNED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "当前状态不允许提交周报（当前状态：" + status + "）");
        }
        if (work == null || work.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "本周工作内容不能为空");
        }
        this.status = ReportStatus.SUBMITTED;
        touch(now);
    }

    /** SUBMITTED → APPROVED / RETURNED；审核人不得是作者本人。 */
    public void review(Long actorId, ReviewDecision decision, String note, Instant now) {
        if (status != ReportStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "只有已提交的周报可以审核（当前状态：" + status + "）");
        }
        if (authorId.equals(actorId)) {
            throw new BusinessException(ErrorCode.SELF_REVIEW_FORBIDDEN, "不能审核自己提交的周报");
        }
        if (decision == ReviewDecision.REJECT && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "退回周报必须填写意见");
        }
        this.status = decision == ReviewDecision.APPROVE ? ReportStatus.APPROVED : ReportStatus.RETURNED;
        this.reviewerId = actorId;
        this.reviewNote = note;
        touch(now);
    }

    public void assertDeletable() {
        if (status != ReportStatus.DRAFT) {
            throw new BusinessException(ErrorCode.REPORT_NOT_DELETABLE);
        }
    }

    public boolean isAuthor(Long userId) {
        return authorId != null && authorId.equals(userId);
    }

    public boolean isLocked() {
        return status == ReportStatus.SUBMITTED || status == ReportStatus.APPROVED;
    }

    /** 周起始日统一归一到周一，避免同一周被写成两个日期。 */
    private static LocalDate normalizeWeekStart(LocalDate weekStart) {
        if (weekStart == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "周起始日不能为空");
        }
        return weekStart.with(DayOfWeek.MONDAY);
    }

    private void touch(Instant now) {
        this.version++;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long projectId() {
        return projectId;
    }

    public Long authorId() {
        return authorId;
    }

    public LocalDate weekStart() {
        return weekStart;
    }

    public String work() {
        return work;
    }

    public String blockers() {
        return blockers;
    }

    public String nextPlan() {
        return nextPlan;
    }

    public ReportStatus status() {
        return status;
    }

    public Long reviewerId() {
        return reviewerId;
    }

    public String reviewNote() {
        return reviewNote;
    }

    public int version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
