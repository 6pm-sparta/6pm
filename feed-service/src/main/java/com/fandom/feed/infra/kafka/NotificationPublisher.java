package com.fandom.feed.infra.kafka;

import com.fandom.feed.global.constant.NotificationPolicy;
import com.fandom.feed.infra.kafka.constant.KafkaTopic;
import com.fandom.feed.infra.kafka.payload.NotificationSendPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {
    private final KafkaTemplate<String, NotificationSendPayload> kafkaTemplate;

    public void publishChunk(UUID postId, UUID cursor, List<UUID> followerChunk) {
        NotificationSendPayload payload = new NotificationSendPayload(
                postId, NotificationPolicy.POST_CREATED, NotificationPolicy.POST_CREATED_TITLE, null, followerChunk
        );

        kafkaTemplate.send(KafkaTopic.NOTIFICATION_SEND, postId.toString(), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null)
                        log.error("게시글 생성 알람 발행 실패 - postId={}, cursor={}, chunkSize={}", postId, cursor, followerChunk.size(), ex);
                });
    }
}