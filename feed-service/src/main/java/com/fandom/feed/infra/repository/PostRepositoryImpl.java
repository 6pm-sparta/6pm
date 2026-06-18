package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.PostRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class PostRepositoryImpl extends BaseRepositoryImpl<Post, UUID, JpaPostRepository> implements PostRepository {
    public PostRepositoryImpl(JpaPostRepository jpaRepository) {
        super(jpaRepository);
    }
}