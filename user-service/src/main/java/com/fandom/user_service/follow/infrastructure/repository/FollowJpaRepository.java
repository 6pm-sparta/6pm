package com.fandom.user_service.follow.infrastructure.repository;

import com.fandom.user_service.follow.domain.entity.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FollowJpaRepository extends JpaRepository<Follow, UUID> {

    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    @EntityGraph(attributePaths = "follower")
    Page<Follow> findByFolloweeId(UUID followeeId, Pageable pageable);

    @EntityGraph(attributePaths = "followee")
    Page<Follow> findByFollowerId(UUID followerId, Pageable pageable);
}
