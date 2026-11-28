package com.labflow.research.domain.repository;

import java.util.List;

/**
 * 实验室作用域端口：回答“这个人在哪个实验室里是什么身份”。
 * <p>由基础设施用只读查询实现，避免业务模块直接依赖 identity 的持久化细节。</p>
 */
public interface LabScopeRepository {

    /** 是否为该实验室的有效成员（含 PI）。 */
    boolean isMember(Long labId, Long userId);

    /** 是否为该实验室的有效负责人（PI）。 */
    boolean isPi(Long labId, Long userId);

    /** 该用户担任 PI 的实验室 ID 列表；没有则返回空列表。 */
    List<Long> findPiLabIds(Long userId);

    /** 该用户所属（成员或 PI）的实验室 ID 列表。 */
    List<Long> findMemberLabIds(Long userId);

    /** 实验室是否存在且启用。 */
    boolean existsEnabled(Long labId);
}
