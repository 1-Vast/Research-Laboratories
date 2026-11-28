package com.labflow.research.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 科研项目聚合根。
 *
 * <p>状态机（非法迁移一律抛 {@link ErrorCode#INVALID_STATE_TRANSITION}，对应 HTTP 409）：</p>
 * <pre>
 * DRAFT --submit--> PENDING --approve--> ACTIVE --requestClose--> CLOSING --approveClose--> ARCHIVED
 *                        |                                         |
 *                        +--reject--> REJECTED --reopen--> DRAFT   +--rejectClose--> ACTIVE
 * </pre>
 *
 * <p>领域层不依赖任何框架类型；持久化由基础设施适配器负责。所有迁移都递增 version，
 * 配合前端回传的版本号实现乐观锁，避免并发审批互相覆盖。</p>
 */
public class Project {

    private Long id;
    private Long labId;
    private Long leaderId;
    private String name;
    private String objective;
    private ProjectStatus status;
    private LocalDate plannedStart;
    private LocalDate plannedEnd;
    private String closeNote;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    private Project() {
    }

    public static Project create(Long labId, Long leaderId, String name, String objective,
                                 LocalDate plannedStart, LocalDate plannedEnd, Instant now) {
        Project project = new Project();
        project.labId = Objects.requireNonNull(labId, "labId");
        project.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        project.name = requireText(name, "项目名称不能为空");
        project.objective = objective;
        project.status = ProjectStatus.DRAFT;
        project.plannedStart = plannedStart;
        project.plannedEnd = plannedEnd;
        project.version = 0;
        project.createdAt = now;
        project.updatedAt = now;
        project.assertPlanValid();
        return project;
    }

    /** 由持久化适配器重建聚合，不做业务校验。 */
    public static Project restore(Long id, Long labId, Long leaderId, String name, String objective,
                                  ProjectStatus status, LocalDate plannedStart, LocalDate plannedEnd,
                                  String closeNote, int version, Instant createdAt, Instant updatedAt) {
        Project project = new Project();
        project.id = id;
        project.labId = labId;
        project.leaderId = leaderId;
        project.name = name;
        project.objective = objective;
        project.status = status;
        project.plannedStart = plannedStart;
        project.plannedEnd = plannedEnd;
        project.closeNote = closeNote;
        project.version = version;
        project.createdAt = createdAt;
        project.updatedAt = updatedAt;
        return project;
    }

    /** 草稿或退回后的项目才允许修改基本信息。 */
    public void updateBasicInfo(String name, String objective, LocalDate plannedStart, LocalDate plannedEnd,
                                Instant now) {
        if (status != ProjectStatus.DRAFT && status != ProjectStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "只有草稿或已驳回的项目可以修改");
        }
        this.name = requireText(name, "项目名称不能为空");
        this.objective = objective;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        assertPlanValid();
        touch(now);
    }

    /** DRAFT → PENDING。 */
    public void submit(Instant now) {
        requireStatus(ProjectStatus.DRAFT, "只有草稿状态的项目可以提交立项");
        this.status = ProjectStatus.PENDING;
        touch(now);
    }

    /** PENDING → ACTIVE；审批人不得是申请人本人。 */
    public void approve(Long reviewerId, Instant now) {
        requireStatus(ProjectStatus.PENDING, "只有待审批的项目可以通过");
        assertNotSelf(reviewerId);
        this.status = ProjectStatus.ACTIVE;
        touch(now);
    }

    /** PENDING → REJECTED；驳回理由必填。 */
    public void reject(Long reviewerId, String reason, Instant now) {
        requireStatus(ProjectStatus.PENDING, "只有待审批的项目可以驳回");
        assertNotSelf(reviewerId);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "驳回必须填写理由");
        }
        this.status = ProjectStatus.REJECTED;
        touch(now);
    }

    /** REJECTED → DRAFT，保留此前的审批轨迹。 */
    public void reopen(Instant now) {
        requireStatus(ProjectStatus.REJECTED, "只有已驳回的项目可以重新编辑");
        this.status = ProjectStatus.DRAFT;
        touch(now);
    }

    /** ACTIVE → CLOSING。是否有未验收任务由应用服务在事务内校验后调用。 */
    public void requestClose(String closeNote, Instant now) {
        requireStatus(ProjectStatus.ACTIVE, "只有进行中的项目可以申请结项");
        this.closeNote = closeNote;
        this.status = ProjectStatus.CLOSING;
        touch(now);
    }

    /** CLOSING → ARCHIVED。 */
    public void approveClose(Long reviewerId, Instant now) {
        requireStatus(ProjectStatus.CLOSING, "只有待结项的项目可以归档");
        assertNotSelf(reviewerId);
        this.status = ProjectStatus.ARCHIVED;
        touch(now);
    }

    /** CLOSING → ACTIVE，退回结项申请。 */
    public void rejectClose(Long reviewerId, String reason, Instant now) {
        requireStatus(ProjectStatus.CLOSING, "只有待结项的项目可以退回");
        assertNotSelf(reviewerId);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "退回结项必须填写理由");
        }
        this.status = ProjectStatus.ACTIVE;
        this.closeNote = null;
        touch(now);
    }

    /** 只有草稿可以删除；已提交的项目必须留痕。 */
    public void assertDeletable() {
        if (status != ProjectStatus.DRAFT) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_DELETABLE);
        }
    }

    /** 成员维护只允许在 ACTIVE 或 CLOSING 阶段进行。 */
    public void assertMemberManageable() {
        if (status != ProjectStatus.ACTIVE && status != ProjectStatus.CLOSING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "只有进行中的项目可以维护成员");
        }
    }

    /** 任务只能在 ACTIVE 阶段创建或推进。 */
    public void assertActiveForTask() {
        if (status != ProjectStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "项目不在进行中，无法操作任务");
        }
    }

    public boolean isLeader(Long userId) {
        return leaderId != null && leaderId.equals(userId);
    }

    public boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }

    private void assertNotSelf(Long reviewerId) {
        if (isLeader(reviewerId)) {
            throw new BusinessException(ErrorCode.SELF_REVIEW_FORBIDDEN, "项目负责人不能审批自己提交的项目");
        }
    }

    private void requireStatus(ProjectStatus expected, String message) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    message + "（当前状态：" + status + "）");
        }
    }

    private void assertPlanValid() {
        if (plannedStart != null && plannedEnd != null && plannedEnd.isBefore(plannedStart)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "计划结束日期不能早于开始日期");
        }
    }

    private void touch(Instant now) {
        this.version++;
        this.updatedAt = now;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value.trim();
    }

    public Long id() {
        return id;
    }

    public Long labId() {
        return labId;
    }

    public Long leaderId() {
        return leaderId;
    }

    public String name() {
        return name;
    }

    public String objective() {
        return objective;
    }

    public ProjectStatus status() {
        return status;
    }

    public LocalDate plannedStart() {
        return plannedStart;
    }

    public LocalDate plannedEnd() {
        return plannedEnd;
    }

    public String closeNote() {
        return closeNote;
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
