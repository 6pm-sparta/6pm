package com.fandom.feed.application.event;

import com.fandom.feed.application.FanoutService;
import com.fandom.feed.global.constant.BroadcastPolicy;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.kafka.NotificationPublisher;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostBroadcastHandler {
    private final FanoutService fanoutService;
    private final UserClient userClient;
    private final NotificationPublisher notificationPublisher;

    public void handle(UUID postId, UUID authorId, String nickname) {
        long followerCount = userClient.countFollowers(authorId).getData();
        boolean shouldFanout = followerCount <= BroadcastPolicy.FANOUT_THRESHOLD;

        UUID cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            CursorPageResponse<UUID> page = userClient.getFollowerIds(authorId, cursor, BroadcastPolicy.CHUNK_SIZE).getData();

            List<UUID> chunk = page.content();
            if (!chunk.isEmpty()) {
                if (shouldFanout) fanoutService.insertChunk(postId, cursor, chunk);
                notificationPublisher.publishChunk(postId, nickname, cursor, chunk);
            }

            cursor = page.nextCursor();
            hasMore = page.hasMore();
        }
    }
}