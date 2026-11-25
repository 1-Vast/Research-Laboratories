package com.labflow.shared.audit;

import com.labflow.shared.api.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计查询用例。
 * <p>作用域：拥有平台管理权限（user:manage）的调用者可查看全部脱敏记录；其余仅能查看与本人相关的记录。
 * 实验室维度的审计范围随第二阶段 project/lab 作用域落地。</p>
 */
@Service
public class AuditQueryService {

    private final AuditEventJpaRepository repository;

    public AuditQueryService(AuditEventJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResult<AuditEventView> search(String action, Long actorId, int page, int size,
                                             boolean platformScope, Long callerId) {
        Long effectiveActorId = platformScope ? actorId : callerId;
        Page<AuditEventJpaEntity> result = repository.search(
                blankToNull(action), effectiveActorId, PageRequest.of(page - 1, size));
        return PageResult.of(result.getContent().stream().map(AuditEventView::of).toList(),
                result.getTotalElements(), page, size);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
