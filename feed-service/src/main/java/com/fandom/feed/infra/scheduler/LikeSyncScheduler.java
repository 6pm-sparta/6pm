package com.fandom.feed.infra.scheduler;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.util.LogContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.fandom.feed.infra.util.LogContext.entry;

@Component
@RequiredArgsConstructor
public class LikeSyncScheduler {
    private final LikeRepository likeRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    @Scheduled(fixedDelayString = "#{${scheduler.like-sync.fixed-delay} * 1000}")
    public void syncLikes() {
        LogContext.info("좋아요 동기화 시작");

        // Redis scan으로 LIKE 키 전체 수집
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(RedisKeyPrefix.LIKE + "*").count(100).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }

        if (keys.isEmpty()) {
            LogContext.info("좋아요 동기화 종료", entry("status", "skipped"));
            return;
        }

        List<Like> likesToInsert = new ArrayList<>();

        keys.forEach(key -> {
            UUID postId = UUID.fromString(key.replace(RedisKeyPrefix.LIKE, ""));
            Set<String> redisUserIds = redisTemplate.opsForSet().members(key);

            if (redisUserIds == null || redisUserIds.isEmpty()) return;

            redisUserIds.forEach(userId ->
                    likesToInsert.add(Like.builder().postId(postId).userId(UUID.fromString(userId)).build())
            );
        });

        try {
            if (!likesToInsert.isEmpty()) {
                likeRepository.batchInsertOnConflictDoNothing(likesToInsert);
                LogContext.info("좋아요 동기화 종료",
                        entry("status", "completed"),
                        entry("keySize", keys.size()),
                        entry("likeSize", likesToInsert.size())
                );
            }
        } catch (Exception e) {
            LogContext.error(e, "좋아요 동기화 실패", entry("keySize", keys.size()), entry("likeSize", likesToInsert.size()));
            throw e;
        }
    }
}