package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaImageRepository extends JpaRepository<Image, UUID> {
    List<Image> findAllByPostIdOrderByOrderAsc(UUID postId);
}