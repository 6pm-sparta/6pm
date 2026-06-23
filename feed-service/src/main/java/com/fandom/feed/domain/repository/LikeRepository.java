package com.fandom.feed.domain.repository;

import com.fandom.feed.domain.entity.Like;

import java.util.List;
import java.util.UUID;

public interface LikeRepository extends BaseRepository<Like, UUID> {
    List<Like> findAllByPostId(UUID postId);
    void deleteByPostIdAndUserId(UUID postId, UUID userId);
}