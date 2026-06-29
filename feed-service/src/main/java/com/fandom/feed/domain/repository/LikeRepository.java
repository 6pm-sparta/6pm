package com.fandom.feed.domain.repository;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.global.constant.ReactionSort;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface LikeRepository extends BaseRepository<Like, UUID> {
    List<Like> findAllByPostId(UUID postId);
    void deleteByPostIdAndUserId(UUID postId, UUID userId);
    List<Like> findByCursorAndUserId(UUID cursor, ReactionSort sort, UUID userId);
    void deleteAllByPostId(UUID postId);
    Map<UUID, List<UUID>> findLikeUsersByPostIds(List<UUID> postIds);
    void batchInsertOnConflictDoNothing(List<Like> likes);
}