package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Comment;
import com.fandom.feed.domain.repository.CommentRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class CommentRepositoryImpl extends BaseRepositoryImpl<Comment, UUID, JpaCommentRepository> implements CommentRepository {
    public CommentRepositoryImpl(JpaCommentRepository jpaRepository) {
        super(jpaRepository);
    }
}