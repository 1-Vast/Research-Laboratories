package com.labflow.research.domain.repository;

import java.util.List;

/** 实验室只读目录：前端建项目时需要选择实验室，这里只暴露当前用户可见的部分。 */
public interface LabDirectoryRepository {

    /** 实验室摘要。 */
    record LabSummary(Long id, String code, String name) {
    }

    /** 当前用户作为成员（含 PI）加入的、且已启用的实验室。 */
    List<LabSummary> findMyLabs(Long userId);
}
