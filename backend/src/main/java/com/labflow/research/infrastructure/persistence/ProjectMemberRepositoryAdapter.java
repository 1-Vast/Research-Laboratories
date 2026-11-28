package com.labflow.research.infrastructure.persistence;

import com.labflow.research.domain.ProjectMember;
import com.labflow.research.domain.ProjectReview;
import com.labflow.research.domain.repository.ProjectMemberRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class ProjectMemberRepositoryAdapter implements ProjectMemberRepository {

    private final ProjectMemberJpaRepository members;
    private final ProjectReviewJpaRepository reviews;

    public ProjectMemberRepositoryAdapter(ProjectMemberJpaRepository members,
                                          ProjectReviewJpaRepository reviews) {
        this.members = members;
        this.reviews = reviews;
    }

    @Override
    public List<ProjectMember> findMembers(Long projectId) {
        return members.findByProjectIdOrderByJoinedAtAsc(projectId).stream()
                .map(ResearchMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ProjectMember> findMember(Long projectId, Long userId) {
        return members.findById(new ProjectMemberId(projectId, userId)).map(ResearchMapper::toDomain);
    }

    @Override
    @Transactional
    public void addMember(ProjectMember member) {
        ProjectMemberId id = new ProjectMemberId(member.projectId(), member.userId());
        ProjectMemberJpaEntity entity = members.findById(id)
                .orElseGet(() -> ProjectMemberJpaEntity.of(member.projectId(), member.userId(),
                        member.memberType(), member.active(), member.joinedAt()));
        // 重新加入时复用同一行，避免复合主键冲突
        entity.setMemberType(member.memberType());
        entity.setActive(member.active());
        members.save(entity);
    }

    @Override
    @Transactional
    public void updateMemberType(Long projectId, Long userId, ProjectMember.Type type) {
        members.findById(new ProjectMemberId(projectId, userId)).ifPresent(entity -> {
            entity.setMemberType(type);
            members.save(entity);
        });
    }

    @Override
    @Transactional
    public void deactivateMember(Long projectId, Long userId) {
        members.findById(new ProjectMemberId(projectId, userId)).ifPresent(entity -> {
            entity.setActive(false);
            members.save(entity);
        });
    }

    @Override
    public boolean isActiveMember(Long projectId, Long userId) {
        return members.existsByProjectIdAndUserIdAndActiveTrue(projectId, userId);
    }

    @Override
    @Transactional
    public void addReview(ProjectReview review) {
        reviews.save(ProjectReviewJpaEntity.of(review.projectId(), review.reviewType(), review.reviewerId(),
                review.decision(), review.reason(), review.reviewedAt()));
    }

    @Override
    public List<ProjectReview> findReviews(Long projectId) {
        return reviews.findByProjectIdOrderByReviewedAtAsc(projectId).stream()
                .map(ResearchMapper::toDomain)
                .toList();
    }
}
