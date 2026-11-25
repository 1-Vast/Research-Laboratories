package com.labflow.shared.audit;

import com.labflow.shared.web.TraceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 审计记录器：与业务写操作同一事务落库（拒绝事件独立提交），只记录脱敏元数据，
 * 不写正文、不写令牌、不写密码。
 */
@Service
public class AuditRecorder {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_DENIED = "DENIED";
    public static final String RESULT_FAILED = "FAILED";

    private final AuditEventJpaRepository repository;
    private final Clock clock;

    public AuditRecorder(AuditEventJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Long actorId, String action, String targetType, Object targetId, String result,
                       String metadata) {
        AuditEventJpaEntity entity = new AuditEventJpaEntity();
        entity.setActorId(actorId);
        entity.setAction(action);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId == null ? null : String.valueOf(targetId));
        entity.setResult(result);
        entity.setTraceId(TraceContext.currentTraceId());
        entity.setMetadata(metadata);
        entity.setCreatedAt(Instant.now(clock));
        repository.save(entity);
    }
}
