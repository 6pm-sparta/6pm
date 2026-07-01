package com.fandom.feed.application.event;

import com.fandom.feed.application.FanoutService;
import com.fandom.feed.global.constant.BroadcastPolicy;
import com.fandom.feed.infra.client.UserClientRetryWrapper;
import com.fandom.feed.infra.kafka.NotificationPublisher;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostBroadcastHandler {
    private final FanoutService fanoutService;
    private final UserClientRetryWrapper userClientRetryWrapper;
    private final NotificationPublisher notificationPublisher;

    public void handlePostCreated(UUID postId, UUID authorId, String nickname) {
        long followerCount = userClientRetryWrapper.countFollowers(authorId);

        if (followerCount == 0) return;

        boolean shouldFanout = followerCount <= BroadcastPolicy.FANOUT_THRESHOLD;

        UUID cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            CursorPageResponse<UUID> page = userClientRetryWrapper.getFollowerIds(authorId, cursor, BroadcastPolicy.CHUNK_SIZE);

            List<UUID> chunk = page.content();
            if (!chunk.isEmpty()) {
                if (shouldFanout) fanoutService.insertChunk(postId, cursor, chunk);
                notificationPublisher.publishChunk(postId, nickname, cursor, chunk);
            }

            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
    }

    public void handlePostDeleted(UUID postId, UUID authorId) {
        long followerCount = userClientRetryWrapper.countFollowers(authorId);

        if (followerCount > BroadcastPolicy.FANOUT_THRESHOLD) return;

        UUID cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            CursorPageResponse<UUID> page = userClientRetryWrapper.getFollowerIds(authorId, cursor, BroadcastPolicy.CHUNK_SIZE);

            List<UUID> chunk = page.content();
            if (!chunk.isEmpty()) fanoutService.removeChunk(postId, cursor, chunk);

            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
    }
}