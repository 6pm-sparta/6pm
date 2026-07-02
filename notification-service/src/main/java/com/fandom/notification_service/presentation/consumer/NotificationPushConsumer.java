package com.fandom.notification_service.presentation.consumer;

import com.fandom.notification_service.application.service.NotificationDispatchService;
import com.fandom.notification_service.infra.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushConsumer {

    private final NotificationDispatchService dispatchService;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_PUSH, groupId = "${spring.kafka.consumer.group-id}-push",
            containerFactory = "pushKafkaListenerContainerFactory")
    public void consume(String notificationId) {
        MDC.put("notificationId", notificationId);
        try {
            log.info("[{}] 수신 id={}", KafkaTopics.NOTIFICATION_PUSH, notificationId);
            dispatchService.dispatch(UUID.fromString(notificationId));
        } finally {
            MDC.clear();
        }
    }
}
