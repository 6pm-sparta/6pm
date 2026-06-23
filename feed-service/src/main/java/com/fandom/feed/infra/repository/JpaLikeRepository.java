package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaLikeRepository extends JpaRepository<Like, UUID> {
}