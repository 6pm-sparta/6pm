package com.fandom.feed.application.event;

import com.fandom.feed.application.FanoutService;
import com.fandom.feed.infra.client.UserClientRetryWrapper;
import com.fandom.feed.infra.kafka.NotificationPublisher;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostBroadcastHandler {
    private final FanoutService fanoutService;
    private final UserClientRetryWrapper userClient;
    private final NotificationPublisher notificationPublisher;

    @Value("${broadcast.fanout-threshold}")
    private long fanoutThreshold;

    public void handlePostCreated(UUID postId, UUID authorId, String nickname) {
        long followerCount = userClient.countFollowers(authorId);

        if (followerCount == 0) return;

        boolean shouldFanout = followerCount <= fanoutThreshold;

        UUID cursor = null;
        boolean hasNext = true;

        while (hasNext) {
            CursorPageResponse<UUID> page = userClient.getFollowerIds(authorId, cursor);

            List<UUID> chunk = page.content();
            if (!chunk.isEmpty()) {
                if (shouldFanout) fanoutService.insertChunk(postId, cursor, chunk);
                notificationPublisher.publishChunk(postId, nickname, cursor, chunk);
            }

            cursor = page.nextCursor();
            hasNext = page.hasNext();
        }
    }

    public void handlePostDeleted(UUID postId, UUID authorId) {
        long followerCount = userClient.countFollowers(authorId);

        if (followerCount == 0 || followerCount > fanoutThreshold) return;

        UUID cursor = null;
        boolean hasNext = true;

        while (hasNext) {
            CursorPageResponse<UUID> page = userClient.getFollowerIds(authorId, cursor);

            List<UUID> chunk = page.content();
            if (!chunk.isEmpty()) fanoutService.removeChunk(postId, cursor, chunk);

            cursor = page.nextCursor();
            hasNext = page.hasNext();
        }
    }
}