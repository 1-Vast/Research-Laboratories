package com.labflow.research.domain;

import java.time.Instant;

/** 项目成员关系：决定“在哪个项目里具备能力”，与系统角色（决定“具备什么能力”）分离。 */
public class ProjectMember {

    /** 成员类型。 */
    public enum Type {
        LEADER,
        MEMBER
    }

    private final Long projectId;
    private final Long userId;
    private final Type memberType;
    private final boolean active;
    private final Instant joinedAt;

    private ProjectMember(Long projectId, Long userId, Type memberType, boolean active, Instant joinedAt) {
        this.projectId = projectId;
        this.userId = userId;
        this.memberType = memberType;
        this.active = active;
        this.joinedAt = joinedAt;
    }

    public static ProjectMember of(Long projectId, Long userId, Type memberType, Instant joinedAt) {
        return new ProjectMember(projectId, userId, memberType, true, joinedAt);
    }

    public static ProjectMember restore(Long projectId, Long userId, Type memberType, boolean active,
                                        Instant joinedAt) {
        return new ProjectMember(projectId, userId, memberType, active, joinedAt);
    }

    public Long projectId() {
        return projectId;
    }

    public Long userId() {
        return userId;
    }

    public Type memberType() {
        return memberType;
    }

    public boolean active() {
        return active;
    }

    public Instant joinedAt() {
        return joinedAt;
    }
}
