package com.fandom.feed.application;

import com.fandom.feed.domain.util.UuidV7TimestampExtractor;
import com.fandom.feed.infra.redis.TimelineCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FanoutService {
    private final TimelineCacheService timelineCacheService;

    /**
     * 팔로워 청크로 타임라인 캐시 추가를 요청하는 메서드
     */
    public void insertChunk(UUID postId, UUID cursor, List<UUID> followerChunk) {
        long score = UuidV7TimestampExtractor.extract(postId);
        try {
            timelineCacheService.addPosts(followerChunk, postId, score);
        } catch (Exception e) {
            log.error("팬아웃 청크 실패 - postId={}, cursor={}, chunkSize={}", postId, cursor, followerChunk.size(), e);
        }
    }
}