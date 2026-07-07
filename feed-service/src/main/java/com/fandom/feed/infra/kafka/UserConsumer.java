package com.fandom.feed.infra.kafka;

import com.fandom.feed.application.CommentService;
import com.fandom.feed.application.LikeService;
import com.fandom.feed.application.PostService;
import com.fandom.feed.infra.kafka.constant.KafkaTopic;
import com.fandom.feed.infra.kafka.idempotency.ProcessedEvent;
import com.fandom.feed.infra.kafka.idempotency.ProcessedEventRepository;
import com.fandom.feed.infra.util.LogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static java.util.Map.entry;

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
        LogContext.info("[UserConsumer] 일반회원 탈퇴 후속 작업 시작", entry("userId", userId));

        String eventKey = KafkaTopic.MEMBER_WITHDRAWN + ":" + userId;
        if (processedEventRepository.existsByEventKey(eventKey)) {
            LogContext.info("[UserConsumer] 일반회원 탈퇴 후속 작업 종료", entry("userId", userId), entry("status", "skipped"));
            return;
        }

        try {
            UUID uuid = UUID.fromString(userId);
            commentService.anonymizeAllByAuthorId(uuid);
            likeService.deleteAllByUserId(uuid);

            processedEventRepository.save(ProcessedEvent.builder().eventKey(eventKey).build());

            LogContext.info("[UserConsumer] 일반회원 탈퇴 후속 작업 종료", entry("userId", userId), entry("status", "completed"));
        } catch (Exception e) {
            LogContext.error(e, "[UserConsumer] 일반회원 탈퇴 후속 작업 실패", entry("userId", userId));
            throw e;
        }
    }

    @KafkaListener(topics = KafkaTopic.CREATOR_WITHDRAWN)
    public void handleCreatorWithdrawn(@Header(KafkaHeaders.RECEIVED_KEY) String userId) {
        LogContext.info("[UserConsumer] 크리에이터 탈퇴 후속 작업 시작", entry("userId", userId));

        String eventKey = KafkaTopic.CREATOR_WITHDRAWN + ":" + userId;
        if (processedEventRepository.existsByEventKey(eventKey)) {
            LogContext.info("[UserConsumer] 크리에이터 탈퇴 후속 작업 종료", entry("userId", userId), entry("status", "skipped"));
            return;
        }
        try {
            UUID uuid = UUID.fromString(userId);
            commentService.anonymizeAllByAuthorId(uuid);
            likeService.deleteAllByUserId(uuid);
            postService.deleteAllByAuthorId(uuid);

            processedEventRepository.save(ProcessedEvent.builder().eventKey(eventKey).build());

            LogContext.info("[UserConsumer] 크리에이터 탈퇴 후속 작업 종료", entry("userId", userId), entry("status", "completed"));
        } catch (Exception e) {
            LogContext.error(e, "[UserConsumer] 크리에이터 탈퇴 후속 작업 실패", entry("userId", userId));
            throw e;
        }
    }
}