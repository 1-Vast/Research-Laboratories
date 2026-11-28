package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.Task;
import com.labflow.research.domain.TaskStatus;
import com.labflow.research.domain.TaskSubmission;
import com.labflow.research.domain.repository.TaskRepository;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class TaskRepositoryAdapter implements TaskRepository {

    private final TaskJpaRepository tasks;
    private final TaskSubmissionJpaRepository submissions;

    public TaskRepositoryAdapter(TaskJpaRepository tasks, TaskSubmissionJpaRepository submissions) {
        this.tasks = tasks;
        this.submissions = submissions;
    }

    @Override
    @Transactional
    public Task save(Task task) {
        TaskJpaEntity entity = task.id() == null
                ? new TaskJpaEntity()
                : tasks.findById(task.id())
                        .orElseThrow(() -> new IllegalStateException("任务不存在: " + task.id()));
        ResearchMapper.copyToEntity(task, entity);
        return ResearchMapper.toDomain(tasks.save(entity));
    }

    @Override
    public Optional<Task> findById(Long id) {
        return tasks.findById(id).map(ResearchMapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(Task task) {
        if (task.id() != null) {
            tasks.deleteById(task.id());
        }
    }

    @Override
    public PageResult<Task> search(Long projectId, TaskStatus status, Long assigneeId, String keyword,
                                   int page, int size, SortSpec sort) {
        String pattern = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Sort.Direction direction = sort.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(direction, sort.field()));
        Page<TaskJpaEntity> result = tasks.search(projectId, status, assigneeId, pattern, pageRequest);
        return PageResult.of(result.getContent().stream().map(ResearchMapper::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public long countNotCanceled(Long projectId) {
        return tasks.countExcluding(projectId, TaskStatus.CANCELED);
    }

    @Override
    public long countDone(Long projectId) {
        return tasks.countByStatus(projectId, TaskStatus.DONE);
    }

    @Override
    public long countOpen(Long projectId) {
        return tasks.countOpen(projectId, TaskStatus.DONE, TaskStatus.CANCELED);
    }

    @Override
    @Transactional
    public TaskSubmission saveSubmission(TaskSubmission submission) {
        TaskSubmissionJpaEntity entity = submission.id() == null
                ? TaskSubmissionJpaEntity.of(submission.taskId(), submission.authorId(),
                        submission.submissionNo(), submission.content(), submission.reviewStatus(),
                        submission.reviewerId(), submission.reviewNote(), submission.reviewedAt(),
                        submission.createdAt())
                : submissions.findById(submission.id())
                        .orElseThrow(() -> new IllegalStateException("交付记录不存在: " + submission.id()));
        if (submission.id() != null) {
            entity.setReviewStatus(submission.reviewStatus());
            entity.setReviewerId(submission.reviewerId());
            entity.setReviewNote(submission.reviewNote());
            entity.setReviewedAt(submission.reviewedAt());
        }
        return ResearchMapper.toDomain(submissions.save(entity));
    }

    @Override
    public List<TaskSubmission> findSubmissions(Long taskId) {
        return submissions.findByTaskIdOrderBySubmissionNoAsc(taskId).stream()
                .map(ResearchMapper::toDomain)
                .toList();
    }

    @Override
    public int nextSubmissionNo(Long taskId) {
        return submissions.maxSubmissionNo(taskId) + 1;
    }
}
