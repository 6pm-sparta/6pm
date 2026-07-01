package com.fandom.chat_service.infra.kafka;

import com.fandom.chat_service.application.port.ChatNotificationPort;
import com.fandom.chat_service.presentation.dto.message.NotificationSendMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaChatNotificationAdapter implements ChatNotificationPort {

    private static final String TYPE_CHAT = "CHAT";

    @Value("${chat.notification.chunk-size:500}")
    private int chunkSize;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void notifyNewMessage(UUID referenceId, String title, String content, List<UUID> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return;
        }
        // 메시지 500 초과시 청크로 나누어 발행
        int total = targetUserIds.size();
        for (int start = 0; start < total; start += chunkSize) {
            List<UUID> chunk = targetUserIds.subList(start, Math.min(start + chunkSize, total));
            NotificationSendMessage message = new NotificationSendMessage(
                    referenceId, TYPE_CHAT, title, content, List.copyOf(chunk));
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_SEND, message);
        }
        log.info("notification.send 발행 reference_id={}, targets={}, chunkSize={}",
                referenceId, total, chunkSize);
    }
}
