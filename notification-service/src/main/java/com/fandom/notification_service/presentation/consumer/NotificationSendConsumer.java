package com.fandom.notification_service.presentation.consumer;

import com.fandom.notification_service.application.dto.CreateNotificationCommand;
import com.fandom.notification_service.application.service.NotificationCommandService;
import com.fandom.notification_service.infra.kafka.KafkaTopics;
import com.fandom.notification_service.presentation.dto.message.NotificationSendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSendConsumer {

    private final NotificationCommandService notificationCommandService;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_SEND, groupId = "${spring.kafka.consumer.group-id}-send")
    public void consume(NotificationSendMessage message) {
        MDC.put("referenceId", String.valueOf(message.referenceId()));
        try {
            log.info("[{}] 수신 reference_id={}, type={}, targets={}",
                    KafkaTopics.NOTIFICATION_SEND, message.referenceId(), message.type(),
                    message.targetUserIds() == null ? 0 : message.targetUserIds().size());

            CreateNotificationCommand command = new CreateNotificationCommand(
                    message.referenceId(),
                    message.type(),
                    message.title(),
                    message.content(),
                    message.targetUserIds()
            );
            notificationCommandService.create(command);
        } finally {
            MDC.clear();
        }
    }
}
