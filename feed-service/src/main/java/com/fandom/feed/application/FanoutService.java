package com.fandom.feed.application;

import com.fandom.feed.infra.util.LogContext;
import com.fandom.feed.infra.util.UuidV7TimestampExtractor;
import com.fandom.feed.infra.redis.TimelineCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static java.util.Map.entry;

@Service
@RequiredArgsConstructor
public class FanoutService {
    private final TimelineCacheService timelineCacheService;

    /** 팔로워 청크로 타임라인 캐시 추가를 요청하는 메서드 */
    public void insertChunk(UUID postId, UUID cursor, List<UUID> followerChunk) {
        long score = UuidV7TimestampExtractor.extract(postId);
        try {
            timelineCacheService.addPost(followerChunk, postId, score);
        } catch (Exception e) {
            LogContext.error(e, "피드 팬아웃 청크 실패",
                    entry("postId", postId),
                    entry("cursor", cursor),
                    entry("chunkSize", followerChunk.size())
            );
        }
    }

    /** 팔로워 청크로 타임라인 캐시 제거를 요청하는 메서드 */
    public void removeChunk(UUID postId, UUID cursor, List<UUID> followerChunk) {
        try {
            timelineCacheService.removePost(followerChunk, postId);
        } catch (Exception e) {
            LogContext.error(e, "피드 팬아웃 제거 청크 실패",
                    entry("postId", postId),
                    entry("cursor", cursor),
                    entry("chunkSize", followerChunk.size())
            );
        }
    }
}