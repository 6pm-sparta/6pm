package com.fandom.feed.infra.kafka;

import com.fandom.feed.global.constant.NotificationPolicy;
import com.fandom.feed.infra.kafka.constant.KafkaTopic;
import com.fandom.feed.infra.kafka.payload.NotificationSendPayload;
import com.fandom.feed.infra.util.LogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static com.fandom.feed.infra.util.LogContext.entry;

@Component
@RequiredArgsConstructor
public class NotificationPublisher {
    private final KafkaTemplate<String, NotificationSendPayload> kafkaTemplate;

    public void publishChunk(UUID postId, String nickname, UUID cursor, List<UUID> followerChunk) {
        NotificationSendPayload payload = new NotificationSendPayload(
                postId,
                NotificationPolicy.POST_CREATED,
                nickname + NotificationPolicy.POST_CREATED_TITLE,
                null,
                followerChunk
        );

        kafkaTemplate.send(KafkaTopic.NOTIFICATION_SEND, postId.toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LogContext.error(ex, "게시글 생성 알람 발행 실패",
                                entry("postId", postId),
                                entry("cursor", cursor),
                                entry("chunkSize", followerChunk.size())
                        );
                    } else {
                        if (cursor == null) {
                            LogContext.info("게시글 알림 청크 발행 성공",
                                    entry("postId", postId),
                                    entry("chunkSize", followerChunk.size())
                            );
                        }
                    }
                });
    }
}