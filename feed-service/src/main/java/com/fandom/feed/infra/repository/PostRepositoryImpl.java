package com.fandom.feed.infra.repository;

import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class PostRepositoryImpl extends BaseRepositoryImpl<Post, UUID, JpaPostRepository> implements PostRepository {
    public PostRepositoryImpl(JpaPostRepository jpaRepository) {
        super(jpaRepository);
    }

    @Override
    public List<Post> findByCursor(UUID cursor, ReactionSort sort, UUID authorId, String keyword) {
        Pageable pageable = PageRequest.of(0, FeedPolicy.PAGE_SIZE + 1);

        return switch (sort) {
            case LATEST -> jpaRepository.findLatest(cursor, authorId, keyword, pageable);
            case OLDEST -> jpaRepository.findOldest(cursor, authorId, keyword, pageable);
        };
    }

    @Override
    public List<Post> findByCursorForWarm(ReactionSort sort) {
        Pageable pageable = PageRequest.of(0, FeedPolicy.MAX_CACHE_SIZE);

        return switch (sort) {
            case LATEST -> jpaRepository.findTopForWarm(pageable);
            case OLDEST -> jpaRepository.findBottomForWarm(pageable);
        };
    }

    @Override
    public void incrementCommentCount(UUID postId) {
        jpaRepository.incrementCommentCount(postId);
    }

    @Override
    public void decrementCommentCount(UUID postId) {
        jpaRepository.decrementCommentCount(postId);
    }
}