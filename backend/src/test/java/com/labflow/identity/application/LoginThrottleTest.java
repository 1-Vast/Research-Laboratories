package com.labflow.identity.application;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("登录失败限流")
class LoginThrottleTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    @DisplayName("窗口内失败达到阈值后拒绝登录")
    void blocksAfterThreshold() {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("alice", "10.0.0.1");
        }
        assertThatThrownBy(() -> throttle.checkAllowed("alice", "10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    @DisplayName("不同用户或不同来源互不影响")
    void isolatesByUsernameAndIp() {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("alice", "10.0.0.1");
        }
        assertThatCode(() -> throttle.checkAllowed("alice", "10.0.0.2")).doesNotThrowAnyException();
        assertThatCode(() -> throttle.checkAllowed("bob", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("成功登录清零计数")
    void resetClearsFailures() {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("alice", "10.0.0.1");
        }
        throttle.reset("alice", "10.0.0.1");
        assertThatCode(() -> throttle.checkAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
        assertThat(throttle.trackedKeys()).isZero();
    }

    @Test
    @DisplayName("窗口过期后重新允许尝试")
    void allowsAgainAfterWindow() {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("alice", "10.0.0.1");
        }
        clock.advance(LoginThrottle.WINDOW.plusMinutes(1));
        assertThatCode(() -> throttle.checkAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();

        throttle.recordFailure("alice", "10.0.0.1");
        assertThatCode(() -> throttle.checkAllowed("alice", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("过期窗口可被清理，避免内存增长")
    void evictsExpiredWindows() {
        throttle.recordFailure("alice", "10.0.0.1");
        assertThat(throttle.trackedKeys()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(11));
        throttle.evictExpired();
        assertThat(throttle.trackedKeys()).isZero();
    }

    /** 可推进的时钟，用于验证固定窗口过期行为。 */
    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
