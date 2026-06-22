package com.fandom.feed.domain.repository;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.application.policy.PostSort;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends BaseRepository<Post, UUID> {
    List<Post> findByCursor(UUID cursor, PostSort sort, UUID authorId, String keyword);
    List<Post> findByCursorForWarm(PostSort sort);
    void incrementCommentCount(UUID postId);
    void decrementCommentCount(UUID postId);
}