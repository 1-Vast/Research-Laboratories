package com.labflow.research.domain.repository;

import com.labflow.research.domain.ProjectMember;

import java.util.List;
import java.util.Optional;

/** 项目成员与审批轨迹的仓储端口。 */
public interface ProjectMemberRepository {

    List<ProjectMember> findMembers(Long projectId);

    Optional<ProjectMember> findMember(Long projectId, Long userId);

    void addMember(ProjectMember member);

    void updateMemberType(Long projectId, Long userId, ProjectMember.Type type);

    void deactivateMember(Long projectId, Long userId);

    /** 判断某用户是否具备指定项目的有效成员关系。 */
    boolean isActiveMember(Long projectId, Long userId);
}
