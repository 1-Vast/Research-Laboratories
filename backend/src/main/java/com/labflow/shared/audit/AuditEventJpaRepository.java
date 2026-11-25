package com.labflow.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, Long> {

    @Query("""
            select a from AuditEventJpaEntity a
            where (:action is null or a.action = :action)
              and (:actorId is null or a.actorId = :actorId)
            order by a.id desc
            """)
    Page<AuditEventJpaEntity> search(@Param("action") String action,
                                     @Param("actorId") Long actorId,
                                     Pageable pageable);
}
