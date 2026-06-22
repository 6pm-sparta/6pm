package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaCommentRepository extends JpaRepository<Comment, UUID> {
}