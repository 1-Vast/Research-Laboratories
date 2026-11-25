package com.labflow.shared.audit;

import java.time.Instant;

/** 审计事件只读视图；不包含令牌、密码或科研正文。 */
public record AuditEventView(
        Long id,
        Long actorId,
        String action,
        String targetType,
        String targetId,
        String result,
        String traceId,
        String metadata,
        Instant createdAt
) {
    static AuditEventView of(AuditEventJpaEntity entity) {
        return new AuditEventView(entity.getId(), entity.getActorId(), entity.getAction(),
                entity.getTargetType(), entity.getTargetId(), entity.getResult(), entity.getTraceId(),
                entity.getMetadata(), entity.getCreatedAt());
    }
}
