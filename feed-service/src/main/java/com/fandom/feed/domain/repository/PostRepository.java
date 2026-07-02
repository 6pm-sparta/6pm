package com.fandom.feed.domain.repository;

import com.fandom.feed.domain.entity.Post;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends BaseRepository<Post, UUID> {
    List<Post> findByCursor(UUID cursor, UUID authorId, String keyword);
    List<Post> findByAuthorIdForWarm(UUID authorId);
    void incrementCommentCount(UUID postId);
    void decrementCommentCount(UUID postId);
    void softDeleteAllByAuthorId(UUID authorId);
    List<UUID> findAllIdsByAuthorId(UUID authorId);
    List<Post> findByCursorAndAuthorIdIn(UUID cursor, List<UUID> authorIds);
    List<UUID> findIdsByCursorAndAuthorIdIn(UUID cursor, List<UUID> authorIds);
    List<Post> findByAuthorIdInForWarm(List<UUID> authorIds);
}