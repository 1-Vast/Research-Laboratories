package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectMember;
import com.labflow.research.domain.ProjectReview;
import com.labflow.research.domain.Report;
import com.labflow.research.domain.Task;
import com.labflow.research.domain.TaskSubmission;

/** 科研领域对象与 JPA 实体之间的映射，只存在于基础设施层。 */
final class ResearchMapper {

    private ResearchMapper() {
    }

    static Project toDomain(ProjectJpaEntity entity) {
        return Project.restore(entity.getId(), entity.getLabId(), entity.getLeaderId(), entity.getName(),
                entity.getObjective(), entity.getStatus(), entity.getPlannedStart(), entity.getPlannedEnd(),
                entity.getCloseNote(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    static void copyToEntity(Project project, ProjectJpaEntity entity) {
        entity.setLabId(project.labId());
        entity.setLeaderId(project.leaderId());
        entity.setName(project.name());
        entity.setObjective(project.objective());
        entity.setStatus(project.status());
        entity.setPlannedStart(project.plannedStart());
        entity.setPlannedEnd(project.plannedEnd());
        entity.setCloseNote(project.closeNote());
        entity.setVersion(project.version());
        entity.setCreatedAt(project.createdAt());
        entity.setUpdatedAt(project.updatedAt());
    }

    static Task toDomain(TaskJpaEntity entity) {
        return Task.restore(entity.getId(), entity.getProjectId(), entity.getAssigneeId(), entity.getTitle(),
                entity.getDescription(), entity.getStatus(), entity.getPriority(), entity.getDueAt(),
                entity.getCancelReason(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    static void copyToEntity(Task task, TaskJpaEntity entity) {
        entity.setProjectId(task.projectId());
        entity.setAssigneeId(task.assigneeId());
        entity.setTitle(task.title());
        entity.setDescription(task.description());
        entity.setStatus(task.status());
        entity.setPriority(task.priority());
        entity.setDueAt(task.dueAt());
        entity.setCancelReason(task.cancelReason());
        entity.setVersion(task.version());
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
    }

    static TaskSubmission toDomain(TaskSubmissionJpaEntity entity) {
        return TaskSubmission.restore(entity.getId(), entity.getTaskId(), entity.getAuthorId(),
                entity.getSubmissionNo(), entity.getContent(), entity.getReviewStatus(),
                entity.getReviewerId(), entity.getReviewNote(), entity.getReviewedAt(), entity.getCreatedAt());
    }

    static Report toDomain(ReportJpaEntity entity) {
        return Report.restore(entity.getId(), entity.getProjectId(), entity.getAuthorId(), entity.getWeekStart(),
                entity.getWork(), entity.getBlockers(), entity.getNextPlan(), entity.getStatus(),
                entity.getReviewerId(), entity.getReviewNote(), entity.getVersion(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static void copyToEntity(Report report, ReportJpaEntity entity) {
        entity.setProjectId(report.projectId());
        entity.setAuthorId(report.authorId());
        entity.setWeekStart(report.weekStart());
        entity.setWork(report.work());
        entity.setBlockers(report.blockers());
        entity.setNextPlan(report.nextPlan());
        entity.setStatus(report.status());
        entity.setReviewerId(report.reviewerId());
        entity.setReviewNote(report.reviewNote());
        entity.setVersion(report.version());
        entity.setCreatedAt(report.createdAt());
        entity.setUpdatedAt(report.updatedAt());
    }

    static ProjectReview toDomain(ProjectReviewJpaEntity entity) {
        return ProjectReview.restore(entity.getId(), entity.getProjectId(), entity.getReviewType(),
                entity.getReviewerId(), entity.getDecision(), entity.getReason(), entity.getReviewedAt());
    }

    static ProjectMember toDomain(ProjectMemberJpaEntity entity) {
        return ProjectMember.restore(entity.getProjectId(), entity.getUserId(), entity.getMemberType(),
                entity.isActive(), entity.getJoinedAt());
    }
}
