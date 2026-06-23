package com.fandom.feed.application;

import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.infra.redis.ReactionCacheService;
import com.fandom.feed.presentation.dto.response.LikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {
    private final PostReader postReader;
    private final ReactionCacheService reactionCacheService;
    private final LikeRepository likeRepository;

    public LikeResponse createLike(UUID postId, UUID userId) {
        postReader.findById(postId);
        long likeCount = reactionCacheService.addLike(postId, userId);
        return LikeResponse.of(postId, likeCount);
    }

    @Transactional
    public LikeResponse deleteLike(UUID postId, UUID userId) {
        postReader.findById(postId);
        likeRepository.deleteByPostIdAndUserId(postId, userId);
        long likeCount = reactionCacheService.removeLike(postId, userId);
        return LikeResponse.of(postId, likeCount);
    }
}