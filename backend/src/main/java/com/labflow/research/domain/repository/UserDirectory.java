package com.labflow.research.domain.repository;

/**
 * 用户目录端口：科研模块只关心「这个用户是否存在且可用」，不关心身份模块如何建模。
 */
public interface UserDirectory {

    boolean existsEnabled(Long userId);
}
