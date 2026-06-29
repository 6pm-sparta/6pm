package com.fandom.chat_service.infra.kafka;

import com.fandom.chat_service.application.port.ChatNotificationPort;
import com.fandom.chat_service.presentation.dto.message.NotificationSendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaChatNotificationAdapter implements ChatNotificationPort {

    private static final String TYPE_CHAT = "CHAT";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void notifyNewMessage(UUID referenceId, String title, String content, List<UUID> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return;
        }
        NotificationSendMessage message = new NotificationSendMessage(
                referenceId, TYPE_CHAT, title, content, targetUserIds);
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_SEND, message);
        log.info("notification.send 발행 reference_id={}, targets={}", referenceId, targetUserIds.size());
    }
}
