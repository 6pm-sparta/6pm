package com.fandom.feed.domain.repository;

import com.fandom.feed.domain.entity.Image;

import java.util.List;
import java.util.UUID;

public interface ImageRepository extends BaseRepository<Image, UUID> {
    List<Image> findAllByPostIdOrderByOrderIndexAsc(UUID postId);
    List<Image> findAllByPostIdInOrderByOrderIndexAsc(List<UUID> postIds);
}