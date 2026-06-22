package com.fandom.notification_service.presentation.consumer;

import com.fandom.notification_service.application.service.UserWithdrawalService;
import com.fandom.notification_service.infra.kafka.KafkaTopics;
import com.fandom.notification_service.presentation.dto.message.UserDeletedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletedConsumer {

    private final UserWithdrawalService userWithdrawalService;

    @KafkaListener(topics = KafkaTopics.USER_DELETED, groupId = "${spring.kafka.consumer.group-id}-user-deleted",
            containerFactory = "userDeletedKafkaListenerContainerFactory")
    public void consume(UserDeletedMessage message) {
        log.info("[{}] 수신 user_id={}", KafkaTopics.USER_DELETED, message.userId());
        if (message.userId() == null) {
            log.warn("[{}] user_id 없음 - 스킵", KafkaTopics.USER_DELETED);
            return;
        }
        userWithdrawalService.handle(message.userId());
    }
}
