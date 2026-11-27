package com.labflow.research.domain.repository;

import com.labflow.research.domain.Report;
import com.labflow.research.domain.ReportStatus;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 周报仓储端口。 */
public interface ReportRepository {

    Report save(Report report);

    Optional<Report> findById(Long id);

    void delete(Report report);

    PageResult<Report> search(Long projectId, Long authorId, ReportStatus status,
                              int page, int size, SortSpec sort);

    boolean existsWeek(Long projectId, Long authorId, LocalDate weekStart);

    void linkTasks(Long reportId, List<Long> taskIds);

    List<Long> findTaskIds(Long reportId);

    /**
     * 批量取关联任务，返回 reportId → taskIds。
     * <p>列表页必须用它：逐条调用 {@link #findTaskIds} 会随页大小线性放大查询次数。</p>
     */
    Map<Long, List<Long>> findTaskIdsByReports(List<Long> reportIds);
}
