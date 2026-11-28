package com.labflow.research.application.command;

import java.time.LocalDate;

/** 修改项目基本信息；version 用于乐观锁。 */
public record UpdateProjectCommand(String name, String objective, LocalDate plannedStart, LocalDate plannedEnd,
                                   Integer version) {
}
