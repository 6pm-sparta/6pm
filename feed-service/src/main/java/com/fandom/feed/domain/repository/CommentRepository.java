package com.fandom.feed.domain.repository;

import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.domain.entity.Comment;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends BaseRepository<Comment, UUID> {
    List<Comment> findByCursorAndPostId(UUID cursor, ReactionSort sort, UUID postId);
    List<Comment> findByCursorAndAuthorId(UUID cursor, ReactionSort sort, UUID authorId);

    void anonymizeAllByAuthorId(UUID authorId);
    void softDeleteAllByPostIds(List<UUID> postIds, UUID userId);
}