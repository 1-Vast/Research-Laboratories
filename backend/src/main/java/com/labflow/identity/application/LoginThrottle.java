package com.labflow.identity.application;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流（进程内实现，第二阶段可替换为 Redis 计数）。
 * <p>按「规范化用户名 + 来源 IP」计数：窗口内失败达到阈值后，即使密码正确也先返回 429，
 * 用固定窗口的粗粒度限流抵御口令暴力枚举。成功登录会清零计数。</p>
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final Clock clock;
    private final Map<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String username, String clientIp) {
        AttemptWindow window = windows.get(key(username, clientIp));
        if (window == null) {
            return;
        }
        Instant now = Instant.now(clock);
        if (window.resetAt().isAfter(now) && window.failures() >= MAX_FAILURES) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "登录失败次数过多，请 10 分钟后再试");
        }
    }

    public void recordFailure(String username, String clientIp) {
        Instant now = Instant.now(clock);
        String key = key(username, clientIp);
        windows.compute(key, (ignored, current) -> {
            if (current == null || !current.resetAt().isAfter(now)) {
                return new AttemptWindow(now.plus(WINDOW), 1);
            }
            return new AttemptWindow(current.resetAt(), current.failures() + 1);
        });
    }

    public void reset(String username, String clientIp) {
        windows.remove(key(username, clientIp));
    }

    /** 清理过期窗口，避免长期运行内存持续增长；由测试或定时任务调用。 */
    public void evictExpired() {
        Instant now = Instant.now(clock);
        windows.entrySet().removeIf(entry -> !entry.getValue().resetAt().isAfter(now));
    }

    public int trackedKeys() {
        return windows.size();
    }

    private String key(String username, String clientIp) {
        return (username == null ? "" : username) + "|" + (clientIp == null ? "" : clientIp);
    }

    record AttemptWindow(Instant resetAt, int failures) {
    }
}
