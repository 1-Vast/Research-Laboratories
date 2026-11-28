package com.labflow.research.application;

import com.labflow.research.domain.Project;
import com.labflow.research.domain.repository.LabScopeRepository;
import com.labflow.research.domain.repository.ProjectMemberRepository;
import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目作用域与归属校验。
 *
 * <p>把「谁能在哪个项目上做什么」集中在一处，避免每个用例各写一遍 if，
 * 也避免 Controller 直接用请求参数判断归属（那是最常见的水平越权来源）。</p>
 *
 * <p>三层模型：系统角色决定能力（{@code @PreAuthorize}）→ 实验室成员关系决定范围（本类）
 * → 项目成员关系决定对象级归属（本类）。</p>
 */
@Service
public class ProjectScopeService {

    private final ProjectMemberRepository members;
    private final LabScopeRepository labScope;

    public ProjectScopeService(ProjectMemberRepository members, LabScopeRepository labScope) {
        this.members = members;
        this.labScope = labScope;
    }

    /** 可读：负责人、项目成员、或该项目所在实验室的 PI。 */
    public boolean canRead(Project project, Long userId) {
        if (project == null || userId == null) {
            return false;
        }
        return project.isLeader(userId)
                || members.isActiveMember(project.id(), userId)
                || labScope.isPi(project.labId(), userId);
    }

    public void assertCanRead(Project project, Long userId) {
        if (!canRead(project, userId)) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_REQUIRED);
        }
    }

    /** 只有项目负责人本人可以改项目、提交立项、维护成员。 */
    public void assertLeader(Project project, Long userId) {
        if (!project.isLeader(userId)) {
            throw new BusinessException(ErrorCode.PROJECT_LEADER_REQUIRED);
        }
    }

    /** 立项 / 结项审批只能由项目所在实验室的 PI 执行。 */
    public void assertLabPi(Project project, Long userId) {
        if (!labScope.isPi(project.labId(), userId)) {
            throw new BusinessException(ErrorCode.LAB_SCOPE_FORBIDDEN, "只有该实验室的 PI 可以审批项目");
        }
    }

    /** 乐观锁：前端回传的版本号必须与库中一致，否则说明已被他人修改。 */
    public void assertVersion(Project project, Integer expectedVersion) {
        if (expectedVersion != null && expectedVersion != project.version()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT,
                    "项目已被他人修改（当前版本 " + project.version() + "），请刷新后重试");
        }
    }

    /** 当前用户担任 PI 的实验室列表，用于列表查询的作用域过滤。 */
    public List<Long> piLabIds(Long userId) {
        return labScope.findPiLabIds(userId);
    }
}
