package com.fandom.feed.infra.kafka;

import com.fandom.feed.application.CommentService;
import com.fandom.feed.application.LikeService;
import com.fandom.feed.application.PostService;
import com.fandom.feed.infra.kafka.constant.KafkaTopic;
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

    @KafkaListener(topics = KafkaTopic.MEMBER_WITHDRAWN)
    public void handleMemberWithdrawn(@Header(KafkaHeaders.RECEIVED_KEY) String userId) {
        UUID uuid = UUID.fromString(userId);
        commentService.anonymizeByAuthorId(uuid);
        likeService.deleteAllByUserId(uuid);
    }

    @KafkaListener(topics = KafkaTopic.CREATOR_WITHDRAWN)
    public void handleCreatorWithdrawn(@Header(KafkaHeaders.RECEIVED_KEY) String userId) {
        UUID uuid = UUID.fromString(userId);
        commentService.anonymizeByAuthorId(uuid);
        likeService.deleteAllByUserId(uuid);
        postService.deleteAllByAuthorId(uuid);
    }
}