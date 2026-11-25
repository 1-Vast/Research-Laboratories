package com.labflow.shared.audit;

import com.labflow.shared.web.TraceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 审计记录器：只记录脱敏元数据，不写正文、不写令牌、不写密码。
 * <ul>
 *   <li>{@link #record} 参与调用方事务：业务回滚时审计一起回滚（成功路径使用）；</li>
 *   <li>{@link #recordIndependent} 开独立事务：登录失败、越权拒绝等「业务已失败」的事件必须留下痕迹，
 *       不能因为外层抛出异常而回滚（对应项目书「拒绝事件独立落库」）。</li>
 * </ul>
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
        save(actorId, action, targetType, targetId, result, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(Long actorId, String action, String targetType, Object targetId, String result,
                                  String metadata) {
        save(actorId, action, targetType, targetId, result, metadata);
    }

    private void save(Long actorId, String action, String targetType, Object targetId, String result,
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
