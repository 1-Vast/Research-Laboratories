package com.labflow.research.domain;

/** 项目状态。合法迁移集中在 {@link Project} 内，禁止由 Controller 直接改。 */
public enum ProjectStatus {
    DRAFT,
    PENDING,
    ACTIVE,
    CLOSING,
    ARCHIVED,
    REJECTED
}
