package com.labflow.identity.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, Long> {

    Optional<RoleJpaEntity> findByCode(String code);

    boolean existsByCode(String code);

    @Query("select r from RoleJpaEntity r left join fetch r.permissions where r.id = :id")
    Optional<RoleJpaEntity> findWithPermissionsById(@Param("id") Long id);

    @Query("select distinct r from RoleJpaEntity r left join fetch r.permissions order by r.id")
    List<RoleJpaEntity> findAllWithPermissions();

    @Query("""
            select r from RoleJpaEntity r
            where (:keyword is null or lower(r.code) like :pattern or lower(r.name) like :pattern)
            """)
    Page<RoleJpaEntity> search(@Param("keyword") String keyword, @Param("pattern") String pattern, Pageable pageable);

    @Query("select count(u) from UserJpaEntity u join u.roles r where r.id = :roleId")
    long countAssignedUsers(@Param("roleId") Long roleId);
}
