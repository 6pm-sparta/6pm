package com.fandom.feed.infra.repository;

import com.fandom.feed.global.constant.FeedPolicy;
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
    public List<Post> findByCursor(UUID cursor, UUID authorId, String keyword) {
        Pageable pageable = PageRequest.of(0, FeedPolicy.PAGE_SIZE + 1);
        return jpaRepository.findByCursor(cursor, authorId, keyword, pageable);
    }

    @Override
    public List<Post> findByCursorForWarm(UUID authorId) {
        Pageable pageable = PageRequest.of(0, FeedPolicy.MAX_CACHE_SIZE);
        return jpaRepository.findByCursorForWarm(authorId, pageable);
    }

    @Override
    public void incrementCommentCount(UUID postId) {
        jpaRepository.incrementCommentCount(postId);
    }

    @Override
    public void decrementCommentCount(UUID postId) {
        jpaRepository.decrementCommentCount(postId);
    }

    @Override
    public void softDeleteAllByAuthorId(UUID authorId) {
        jpaRepository.softDeleteAllByAuthorId(authorId);
    }

    @Override
    public List<UUID> findAllIdsByAuthorId(UUID authorId) {
        return jpaRepository.findAllIdsByAuthorId(authorId);
    }
}