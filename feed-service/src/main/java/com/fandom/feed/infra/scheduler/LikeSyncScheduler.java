package com.fandom.feed.infra.scheduler;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeSyncScheduler {
    private final LikeRepository likeRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedDelayString = "#{${scheduler.like-sync.fixed-delay} * 1000}")
    public void syncLikes() {
        // Redis scan으로 LIKE_SET 키 전체 수집
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(RedisKeyPrefix.LIKE_SET + "*").count(100).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }

        if (keys.isEmpty()) return;

        List<Like> likesToInsert = new ArrayList<>();

        keys.forEach(key -> {
            UUID postId = UUID.fromString(key.replace(RedisKeyPrefix.LIKE_SET, ""));
            Set<String> redisUserIds = redisTemplate.opsForSet().members(key);

            if (redisUserIds == null || redisUserIds.isEmpty()) return;

            redisUserIds.forEach(userId ->
                    likesToInsert.add(Like.builder().postId(postId).userId(UUID.fromString(userId)).build())
            );
        });

        if (!likesToInsert.isEmpty())
            likeRepository.batchInsertOnConflictDoNothing(likesToInsert);
    }
}