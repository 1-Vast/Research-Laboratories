package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, Long> {

    /**
     * 作用域过滤：只返回「我是 PI 的实验室下的项目」或「我是项目成员的项目」。
     * <p>piLabIds 为空时由适配器传入占位值 -1，避免 JPQL 的 in () 语法问题。</p>
     */
    @Query("""
            select p from ProjectJpaEntity p
            where (p.labId in :piLabIds
                   or exists (select 1 from ProjectMemberJpaEntity m
                              where m.projectId = p.id and m.userId = :userId and m.active = true))
              and (:labId is null or p.labId = :labId)
              and (:status is null or p.status = :status)
              and (:pattern is null or lower(p.name) like :pattern)
            """)
    Page<ProjectJpaEntity> searchScoped(@Param("piLabIds") List<Long> piLabIds,
                                        @Param("userId") Long userId,
                                        @Param("labId") Long labId,
                                        @Param("status") ProjectStatus status,
                                        @Param("pattern") String pattern,
                                        Pageable pageable);
}
