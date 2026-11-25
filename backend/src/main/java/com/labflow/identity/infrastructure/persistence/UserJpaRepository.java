package com.labflow.identity.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from UserJpaEntity u where u.id = :id")
    Optional<UserJpaEntity> findWithAuthoritiesById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from UserJpaEntity u where u.username = :username")
    Optional<UserJpaEntity> findWithAuthoritiesByUsername(@Param("username") String username);

    @Query("""
            select u from UserJpaEntity u
            where (:keyword is null
                   or lower(u.username) like :pattern
                   or lower(coalesce(u.displayName, '')) like :pattern
                   or lower(coalesce(u.email, '')) like :pattern)
            """)
    Page<UserJpaEntity> search(@Param("keyword") String keyword, @Param("pattern") String pattern, Pageable pageable);

    @Query("select count(distinct u) from UserJpaEntity u join u.roles r where r.code = :roleCode and u.enabled = true")
    long countEnabledUsersWithRole(@Param("roleCode") String roleCode);

    @Query("""
            select count(distinct u) from UserJpaEntity u join u.roles r
            where r.code = :roleCode and u.enabled = true and u.id <> :excludedId
            """)
    long countOtherEnabledUsersWithRole(@Param("excludedId") Long excludedId, @Param("roleCode") String roleCode);
}
