package com.labflow.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。HTTP 状态与业务码分离：前端根据 code 做提示，后端根据 status 做拦截。
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "参数校验失败"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求不合法"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未认证或登录状态已失效"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "登录凭证无效"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "登录凭证已过期"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "登录状态已注销"),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "账号已被禁用"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "没有执行该操作的权限"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "用户不存在"),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "角色不存在"),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "项目不存在"),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "任务不存在"),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "周报不存在"),
    LAB_NOT_FOUND(HttpStatus.NOT_FOUND, "实验室不存在"),

    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "用户名已被占用"),
    DUPLICATE_ROLE_CODE(HttpStatus.CONFLICT, "角色编码已存在"),
    CONFLICT(HttpStatus.CONFLICT, "数据状态冲突，请刷新后重试"),
    LAST_ADMIN_PROTECTED(HttpStatus.CONFLICT, "不能禁用或降级最后一个可用的系统管理员"),
    PROTECTED_ROLE(HttpStatus.CONFLICT, "内置角色不允许修改或删除"),
    ROLE_IN_USE(HttpStatus.CONFLICT, "角色仍被用户引用，无法删除"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "当前状态不允许该操作"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "数据已被他人修改，请刷新后重试"),
    DUPLICATE_REPORT_WEEK(HttpStatus.CONFLICT, "该周周报已存在"),
    OPEN_TASKS_EXIST(HttpStatus.CONFLICT, "仍有未验收任务，无法申请结项"),
    PROJECT_NOT_DELETABLE(HttpStatus.CONFLICT, "只有草稿状态的项目可以删除"),
    TASK_NOT_DELETABLE(HttpStatus.CONFLICT, "只有未开始的任务可以删除"),
    REPORT_NOT_DELETABLE(HttpStatus.CONFLICT, "只有草稿状态的周报可以删除"),

    SELF_REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "不能审批自己提交的内容"),
    PROJECT_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "不是该项目的成员"),
    LAB_SCOPE_FORBIDDEN(HttpStatus.FORBIDDEN, "不在该实验室的授权范围内"),
    PROJECT_LEADER_REQUIRED(HttpStatus.FORBIDDEN, "只有项目负责人可以执行该操作"),


    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后重试"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
