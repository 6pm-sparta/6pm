package com.fandom.user_service.follow.infrastructure.repository;

import com.fandom.user_service.follow.domain.entity.Follow;
import com.fandom.user_service.follow.domain.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FollowRepositoryImpl implements FollowRepository {

    private final FollowJpaRepository followJpaRepository;

    @Override
    public Follow saveAndFlush(Follow follow) {
        return followJpaRepository.saveAndFlush(follow);
    }

    @Override
    public void delete(Follow follow) {
        followJpaRepository.delete(follow);
    }

    @Override
    public boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId) {
        return followJpaRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Override
    public Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId) {
        return followJpaRepository.findByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Override
    public Page<Follow> findByFolloweeId(UUID followeeId, Pageable pageable) {
        return followJpaRepository.findByFolloweeId(followeeId, pageable);
    }

    @Override
    public Page<Follow> findByFollowerId(UUID followerId, Pageable pageable) {
        return followJpaRepository.findByFollowerId(followerId, pageable);
    }
}
