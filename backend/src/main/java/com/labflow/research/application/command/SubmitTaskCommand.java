package com.labflow.research.application.command;

/** 提交任务交付内容。 */
public record SubmitTaskCommand(String content, Integer version) {
}
