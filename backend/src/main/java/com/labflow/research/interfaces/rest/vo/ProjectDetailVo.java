package com.labflow.research.interfaces.rest.vo;

import java.util.List;

/** 项目详情：项目本体 + 进度 + 成员。 */
public record ProjectDetailVo(ProjectVo project, ProgressVo progress, List<ProjectMemberVo> members) {
}
