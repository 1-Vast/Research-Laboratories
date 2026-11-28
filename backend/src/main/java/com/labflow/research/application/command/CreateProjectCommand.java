package com.labflow.research.application.command;

import java.time.LocalDate;

/** 创建项目草稿。负责人默认为创建人本人。 */
public record CreateProjectCommand(Long labId, String name, String objective,
                                   LocalDate plannedStart, LocalDate plannedEnd) {
}
