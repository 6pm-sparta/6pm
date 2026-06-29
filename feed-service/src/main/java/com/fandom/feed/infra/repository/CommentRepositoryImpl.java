package com.fandom.feed.infra.repository;

import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.domain.entity.Comment;
import com.fandom.feed.domain.repository.CommentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class CommentRepositoryImpl extends BaseRepositoryImpl<Comment, UUID, JpaCommentRepository> implements CommentRepository {
    private static final Pageable PAGEABLE = PageRequest.of(0, FeedPolicy.PAGE_SIZE + 1);

    public CommentRepositoryImpl(JpaCommentRepository jpaRepository) {
        super(jpaRepository);
    }

    @Override
    public List<Comment> findByCursorAndPostId(UUID cursor, ReactionSort sort, UUID postId) {
        return switch (sort) {
            case LATEST -> jpaRepository.findLatestByPostId(cursor, postId, PAGEABLE);
            case OLDEST -> jpaRepository.findOldestByPostId(cursor, postId, PAGEABLE);
        };
    }

    @Override
    public List<Comment> findByCursorAndAuthorId(UUID cursor, ReactionSort sort, UUID authorId) {
        return switch (sort) {
            case LATEST -> jpaRepository.findLatestByAuthorId(cursor, authorId, PAGEABLE);
            case OLDEST -> jpaRepository.findOldestByAuthorId(cursor, authorId, PAGEABLE);
        };
    }

    @Override
    public void softDeleteAllByPostId(UUID postId, UUID userId) {
        jpaRepository.softDeleteAllByPostId(postId, userId, LocalDateTime.now());
    }
}