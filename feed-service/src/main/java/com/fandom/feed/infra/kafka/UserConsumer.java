package com.fandom.feed.infra.kafka;

import com.fandom.feed.application.CommentService;
import com.fandom.feed.application.LikeService;
import com.fandom.feed.application.PostService;
import com.fandom.feed.infra.kafka.constant.KafkaTopic;
import com.fandom.feed.infra.kafka.idempotency.ProcessedEvent;
import com.fandom.feed.infra.kafka.idempotency.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional
public class UserConsumer {
    private final PostService postService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = KafkaTopic.MEMBER_WITHDRAWN)
    public void handleMemberWithdrawn(@Header(KafkaHeaders.RECEIVED_KEY) String userId) {
        String eventKey = KafkaTopic.MEMBER_WITHDRAWN + ":" + userId;
        if (processedEventRepository.existsByEventKey(eventKey)) return;

        UUID uuid = UUID.fromString(userId);
        commentService.anonymizeAllByAuthorId(uuid);
        likeService.deleteAllByUserId(uuid);

        processedEventRepository.save(ProcessedEvent.builder().eventKey(eventKey).build());
    }

    @KafkaListener(topics = KafkaTopic.CREATOR_WITHDRAWN)
    public void handleCreatorWithdrawn(@Header(KafkaHeaders.RECEIVED_KEY) String userId) {
        String eventKey = KafkaTopic.CREATOR_WITHDRAWN + ":" + userId;
        if (processedEventRepository.existsByEventKey(eventKey)) return;

        UUID uuid = UUID.fromString(userId);
        commentService.anonymizeAllByAuthorId(uuid);
        likeService.deleteAllByUserId(uuid);
        postService.deleteAllByAuthorId(uuid);

        processedEventRepository.save(ProcessedEvent.builder().eventKey(eventKey).build());
    }
}