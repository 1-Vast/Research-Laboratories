package com.labflow.research.domain.repository;

import com.labflow.research.domain.Report;
import com.labflow.research.domain.ReportStatus;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;

import java.time.LocalDate;
import java.util.List;
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
}
