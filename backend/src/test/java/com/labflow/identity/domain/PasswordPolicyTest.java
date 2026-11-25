package com.labflow.identity.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("密码策略")
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(8, 64);

    @Test
    @DisplayName("合法密码通过校验")
    void acceptsValidPassword() {
        assertThatCode(() -> policy.validate("Passw0rd1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("长度不足被拒绝")
    void rejectsTooShort() {
        assertThatThrownBy(() -> policy.validate("Ab1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能少于");
    }

    @Test
    @DisplayName("纯字母或纯数字被拒绝")
    void rejectsSingleCharacterClass() {
        assertThatThrownBy(() -> policy.validate("abcdefgh"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字母和数字");
        assertThatThrownBy(() -> policy.validate("12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字母和数字");
    }

    @Test
    @DisplayName("包含空白字符被拒绝")
    void rejectsWhitespace() {
        assertThatThrownBy(() -> policy.validate("Pass w0rd1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("空白");
    }

    @Test
    @DisplayName("下限不会低于课程要求的 6 位")
    void minLengthIsAtLeastSix() {
        assertThat(new PasswordPolicy(2, 64).minLength()).isEqualTo(6);
    }

    @Test
    @DisplayName("超长密码被拒绝，避免 BCrypt 截断歧义")
    void rejectsTooLong() {
        assertThatThrownBy(() -> policy.validate("a1" + "x".repeat(80)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
