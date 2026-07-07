package com.fandom.feed.infra.repository;

import com.fandom.feed.domain.entity.Image;
import com.fandom.feed.domain.repository.ImageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ImageRepositoryImpl extends BaseRepositoryImpl<Image, UUID, JpaImageRepository> implements ImageRepository {
    public ImageRepositoryImpl(JpaImageRepository jpaRepository) {
        super(jpaRepository);
    }

    @Override
    public List<Image> findAllByPostIdOrderByOrderIndexAsc(UUID postId) {
        return jpaRepository.findAllByPostIdOrderByOrderIndexAsc(postId);
    }

    @Override
    public List<Image> findAllByPostIdInOrderByOrderIndexAsc(List<UUID> postIds) {
        return jpaRepository.findAllByPostIdInOrderByOrderIndexAsc(postIds);
    }

    @Override
    public void deleteAllByPostIdIn(List<UUID> postIds) {
        jpaRepository.deleteAllByPostIdIn(postIds);
    }
}