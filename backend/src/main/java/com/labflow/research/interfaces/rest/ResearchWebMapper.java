package com.labflow.research.interfaces.rest;

import com.labflow.research.domain.Project;
import com.labflow.research.domain.ProjectMember;
import com.labflow.research.domain.ProjectReview;
import com.labflow.research.domain.Task;
import com.labflow.research.domain.TaskSubmission;
import com.labflow.research.interfaces.rest.vo.ProgressVo;
import com.labflow.research.interfaces.rest.vo.ProjectMemberVo;
import com.labflow.research.interfaces.rest.vo.ProjectReviewVo;
import com.labflow.research.interfaces.rest.vo.ProjectVo;
import com.labflow.research.interfaces.rest.vo.TaskSubmissionVo;
import com.labflow.research.interfaces.rest.vo.TaskVo;

/** 科研业务领域对象到返回体的映射。 */
public final class ResearchWebMapper {

    private ResearchWebMapper() {
    }

    public static ProjectVo toProjectVo(Project project) {
        return new ProjectVo(project.id(), project.labId(), project.leaderId(), project.name(),
                project.objective(), project.status(), project.plannedStart(), project.plannedEnd(),
                project.closeNote(), project.version(), project.createdAt(), project.updatedAt());
    }

    public static ProgressVo toProgressVo(long total, long done) {
        int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        return new ProgressVo(total, done, percent);
    }

    public static ProjectMemberVo toMemberVo(ProjectMember member) {
        return new ProjectMemberVo(member.userId(), member.memberType(), member.active(), member.joinedAt());
    }

    public static ProjectReviewVo toReviewVo(ProjectReview review) {
        return new ProjectReviewVo(review.id(), review.projectId(), review.reviewType(), review.reviewerId(),
                review.decision(), review.reason(), review.reviewedAt());
    }

    public static TaskVo toTaskVo(Task task) {
        return new TaskVo(task.id(), task.projectId(), task.assigneeId(), task.title(), task.description(),
                task.status(), task.priority(), task.dueAt(), task.cancelReason(), task.version(),
                task.createdAt(), task.updatedAt());
    }

    public static TaskSubmissionVo toSubmissionVo(TaskSubmission submission) {
        return new TaskSubmissionVo(submission.id(), submission.taskId(), submission.authorId(),
                submission.submissionNo(), submission.content(), submission.reviewStatus(),
                submission.reviewerId(), submission.reviewNote(), submission.reviewedAt(),
                submission.createdAt());
    }
}
