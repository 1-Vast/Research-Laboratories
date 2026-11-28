package com.labflow.research.application.command;

/** 申请结项命令。 */
public record CloseProjectCommand(String closeNote, Integer version) {
}
