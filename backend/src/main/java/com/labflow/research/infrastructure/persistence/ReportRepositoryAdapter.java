package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.Report;
import com.labflow.research.domain.ReportStatus;
import com.labflow.research.domain.repository.ReportRepository;
import com.labflow.shared.api.PageResult;
import com.labflow.shared.api.SortSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class ReportRepositoryAdapter implements ReportRepository {

    private final ReportJpaRepository reports;
    private final ReportTaskJpaRepository reportTasks;

    public ReportRepositoryAdapter(ReportJpaRepository reports, ReportTaskJpaRepository reportTasks) {
        this.reports = reports;
        this.reportTasks = reportTasks;
    }

    @Override
    @Transactional
    public Report save(Report report) {
        ReportJpaEntity entity = report.id() == null
                ? new ReportJpaEntity()
                : reports.findById(report.id())
                        .orElseThrow(() -> new IllegalStateException("周报不存在: " + report.id()));
        ResearchMapper.copyToEntity(report, entity);
        return ResearchMapper.toDomain(reports.save(entity));
    }

    @Override
    public Optional<Report> findById(Long id) {
        return reports.findById(id).map(ResearchMapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(Report report) {
        if (report.id() != null) {
            reportTasks.deleteByReportId(report.id());
            reports.deleteById(report.id());
        }
    }

    @Override
    public PageResult<Report> search(Long projectId, Long authorId, ReportStatus status,
                                     int page, int size, SortSpec sort) {
        Sort.Direction direction = sort.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(direction, sort.field()));
        Page<ReportJpaEntity> result = reports.search(projectId, authorId, status, pageRequest);
        return PageResult.of(result.getContent().stream().map(ResearchMapper::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public boolean existsWeek(Long projectId, Long authorId, LocalDate weekStart) {
        return reports.existsByProjectIdAndAuthorIdAndWeekStart(projectId, authorId, weekStart);
    }

    @Override
    @Transactional
    public void linkTasks(Long reportId, List<Long> taskIds) {
        reportTasks.deleteByReportId(reportId);
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        List<ReportTaskJpaEntity> links = taskIds.stream()
                .distinct()
                .map(taskId -> ReportTaskJpaEntity.of(reportId, taskId))
                .toList();
        reportTasks.saveAll(links);
    }

    @Override
    public List<Long> findTaskIds(Long reportId) {
        return reportTasks.findByReportId(reportId).stream()
                .map(ReportTaskJpaEntity::getTaskId)
                .toList();
    }

    @Override
    public Map<Long, List<Long>> findTaskIdsByReports(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            return Map.of();
        }
        // 一次 IN 查询取回整页关联；空列表的周报不会出现在结果里，由调用方补空集合。
        return reportTasks.findByReportIdIn(reportIds).stream()
                .collect(Collectors.groupingBy(ReportTaskJpaEntity::getReportId,
                        Collectors.mapping(ReportTaskJpaEntity::getTaskId, Collectors.toList())));
    }
}
