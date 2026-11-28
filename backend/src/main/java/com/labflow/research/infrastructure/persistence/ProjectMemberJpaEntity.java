package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.ProjectMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

/** project_members 表的 JPA 映射（复合主键）。 */
@Entity
@Table(name = "project_members")
@IdClass(ProjectMemberId.class)
public class ProjectMemberJpaEntity {

    @Id
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 16)
    private ProjectMember.Type memberType;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected ProjectMemberJpaEntity() {
    }

    public static ProjectMemberJpaEntity of(Long projectId, Long userId, ProjectMember.Type memberType,
                                            boolean active, Instant joinedAt) {
        ProjectMemberJpaEntity entity = new ProjectMemberJpaEntity();
        entity.projectId = projectId;
        entity.userId = userId;
        entity.memberType = memberType;
        entity.active = active;
        entity.joinedAt = joinedAt;
        return entity;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public ProjectMember.Type getMemberType() {
        return memberType;
    }

    public void setMemberType(ProjectMember.Type memberType) {
        this.memberType = memberType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
