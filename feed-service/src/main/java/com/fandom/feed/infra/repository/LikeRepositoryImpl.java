package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class LikeRepositoryImpl extends BaseRepositoryImpl<Like, UUID, JpaLikeRepository> implements LikeRepository {
    public LikeRepositoryImpl(JpaLikeRepository jpaRepository) {
        super(jpaRepository);
    }

    @Override
    public List<Like> findAllByPostId(UUID postId) {
        return jpaRepository.findAllByPostId(postId);
    }

    @Override
    public void deleteByPostIdAndUserId(UUID postId, UUID userId) {
        jpaRepository.deleteByPostIdAndUserId(postId, userId);
    }

    @Override
    public List<Like> findByCursorAndUserId(UUID cursor, ReactionSort sort, UUID userId) {
        Pageable pageable = PageRequest.of(0, FeedPolicy.PAGE_SIZE + 1);

        return switch (sort) {
            case LATEST -> jpaRepository.findLatestByUserId(cursor, userId, pageable);
            case OLDEST -> jpaRepository.findOldestByUserId(cursor, userId, pageable);
        };
    }

    @Override
    public List<Like> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void deleteAllByPostId(UUID postId) {
        jpaRepository.deleteAllByPostId(postId);
    }

    @Override
    public Map<UUID, List<UUID>> findLikeUsersByPostIds(List<UUID> postIds) {
        return jpaRepository.findLikeUsersByPostIds(postIds)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (UUID) row[0],
                        Collectors.mapping(row -> (UUID) row[1], Collectors.toList())
                ));
    }
}