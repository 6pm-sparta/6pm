package com.fandom.feed.infra.repository;

import com.fandom.feed.application.policy.PostPolicy;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.application.policy.PostSort;
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
    public List<Post> findByCursor(UUID cursor, PostSort sort, UUID authorId, String keyword) {
        Pageable pageable = PageRequest.of(0, PostPolicy.PAGE_SIZE + 1);

        return switch (sort) {
            case LATEST -> jpaRepository.findLatest(cursor, authorId, keyword, pageable);
            case OLDEST -> jpaRepository.findOldest(cursor, authorId, keyword, pageable);
        };
    }

    @Override
    public List<Post> findByCursorForWarm(PostSort sort) {
        Pageable pageable = PageRequest.of(0, PostPolicy.MAX_CACHE_SIZE);

        return switch (sort) {
            case LATEST -> jpaRepository.findTopForWarm(pageable);
            case OLDEST -> jpaRepository.findBottomForWarm(pageable);
        };
    }
}