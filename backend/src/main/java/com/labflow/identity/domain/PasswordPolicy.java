package com.labflow.identity.domain;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;

/**
 * 密码策略（后端校验，不依赖前端）：长度区间内、同时包含字母与数字、不含空白字符。
 * 项目书建议最少 8 位；配置文件可调，但下限不会低于课程要求的 6 位。
 */
public class PasswordPolicy {

    private final int minLength;
    private final int maxLength;

    public PasswordPolicy(int minLength, int maxLength) {
        this.minLength = Math.max(6, minLength);
        this.maxLength = maxLength <= 0 ? 64 : maxLength;
    }

    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码不能为空");
        }
        if (rawPassword.length() < minLength) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码长度不能少于 " + minLength + " 位");
        }
        if (rawPassword.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码长度不能超过 " + maxLength + " 位");
        }
        if (rawPassword.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码不能包含空白字符");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码必须同时包含字母和数字");
        }
    }

    public int minLength() {
        return minLength;
    }

    public int maxLength() {
        return maxLength;
    }
}
