package com.labflow.research.interfaces.rest.vo;

/** 项目进度：total 为非取消任务数，done 为已验收任务数。 */
public record ProgressVo(long total, long done, int percent) {
}
