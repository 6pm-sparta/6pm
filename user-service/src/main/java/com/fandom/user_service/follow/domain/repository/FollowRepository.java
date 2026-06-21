package com.fandom.user_service.follow.domain.repository;

import com.fandom.user_service.follow.domain.entity.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    Page<Follow> findByFolloweeId(UUID followeeId, Pageable pageable);

    Page<Follow> findByFollowerId(UUID followerId, Pageable pageable);
}
