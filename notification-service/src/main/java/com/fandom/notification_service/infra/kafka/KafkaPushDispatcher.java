package com.fandom.notification_service.infra.kafka;

import com.fandom.notification_service.application.port.PushDispatchPort;
//import com.fandom.notification_service.presentation.dto.PushFailedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KafkaPushDispatcher implements PushDispatchPort {

    private final KafkaTemplate<String, String> pushKafkaTemplate;
    private final KafkaTemplate<String, Object> retryKafkaTemplate;

    @Override
    public void dispatch(UUID notificationId, UUID userId) {
        pushKafkaTemplate.send(KafkaTopics.NOTIFICATION_PUSH, userId.toString(), notificationId.toString());
    }

    @Override
    public void publishRetry(UUID notificationId, String deviceToken) {

    }
}
