package com.fandom.feed.domain.repository;

import com.fandom.feed.global.constant.ReactionSort;
import com.fandom.feed.domain.entity.Post;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends BaseRepository<Post, UUID> {
    List<Post> findByCursor(UUID cursor, ReactionSort sort, UUID authorId, String keyword);
    List<Post> findByCursorForWarm(ReactionSort sort);
    void incrementCommentCount(UUID postId);
    void decrementCommentCount(UUID postId);
}