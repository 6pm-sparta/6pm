package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class LikeRepositoryImpl extends BaseRepositoryImpl<Like, UUID, JpaLikeRepository> implements LikeRepository {
    public LikeRepositoryImpl(JpaLikeRepository jpaRepository) {
        super(jpaRepository);
    }

    @Override
    public List<Like> findAllByPostId(UUID postId) {
        return jpaRepository.findAllByPostId(postId);
    }

    @Override
    public void deleteByPostIdAndUserId(UUID postId, UUID userId) {
        jpaRepository.deleteByPostIdAndUserId(postId, userId);
    }
}