package com.fandom.feed.infra.scheduler;

import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeSyncScheduler {
    private final LikeRepository likeRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedDelayString = "#{${scheduler.like-sync.fixed-delay} * 1000}")
    public void syncLikes() {
        Map<UUID, Set<UUID>> dbLikes = likeRepository.findAll()
                .stream()
                .collect(Collectors.groupingBy(
                        Like::getPostId,
                        Collectors.mapping(Like::getUserId, Collectors.toSet())
                ));

        dbLikes.forEach((postId, userIds) -> {
            String key = RedisKeyPrefix.LIKE_SET + postId;
            Set<String> redisUserIds = redisTemplate.opsForSet().members(key);

            if (redisUserIds == null || redisUserIds.isEmpty()) return;

            Set<String> dbUserIdStrs = userIds.stream().map(UUID::toString).collect(Collectors.toSet());

            Set<String> toInsert = new HashSet<>(redisUserIds);
            toInsert.removeAll(dbUserIdStrs);

            if (!toInsert.isEmpty()) {
                List<Like> likesToInsert = toInsert.stream()
                        .map(userId -> Like.builder().postId(postId).userId(UUID.fromString(userId)).build())
                        .toList();
                likeRepository.saveAll(likesToInsert);
            }
        });
    }
}