package com.labflow.research.domain.repository;

import com.labflow.research.domain.Task;
import com.labflow.research.domain.TaskStatus;
import com.labflow.research.domain.TaskSubmission;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;

import java.util.List;
import java.util.Optional;

/** 任务与交付快照的仓储端口。 */
public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findById(Long id);

    void delete(Task task);

    PageResult<Task> search(Long projectId, TaskStatus status, Long assigneeId, String keyword,
                            int page, int size, SortSpec sort);

    /** 项目下非取消任务总数（进度分母）。 */
    long countNotCanceled(Long projectId);

    /** 项目下已验收任务数（进度分子）。 */
    long countDone(Long projectId);

    /** 项目下未验收且未取消的任务数，用于结项前检查。 */
    long countOpen(Long projectId);

    TaskSubmission saveSubmission(TaskSubmission submission);

    List<TaskSubmission> findSubmissions(Long taskId);

    int nextSubmissionNo(Long taskId);
}
