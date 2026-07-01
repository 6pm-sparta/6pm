package com.fandom.notification_service.presentation.consumer;

import com.fandom.notification_service.application.service.NotificationDispatchService;
import com.fandom.notification_service.infra.kafka.KafkaTopics;
import com.fandom.notification_service.presentation.dto.message.PushFailedMessage;
import com.fandom.notification_service.support.LogMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushRetryConsumer {

    private final NotificationDispatchService dispatchService;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_PUSH_FAILED, groupId = "${spring.kafka.consumer.group-id}-push-failed",
            containerFactory = "pushFailedKafkaListenerContainerFactory")
    public void consume(PushFailedMessage message) {
        log.info("[{}] 수신 id={}, token={}",
                KafkaTopics.NOTIFICATION_PUSH_FAILED, message.notificationId(), LogMask.token(message.deviceToken()));
        dispatchService.retry(message.notificationId(), message.deviceToken());
    }
}
