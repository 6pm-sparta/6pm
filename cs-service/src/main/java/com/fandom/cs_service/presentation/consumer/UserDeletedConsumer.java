package com.fandom.cs_service.presentation.consumer;

import com.fandom.cs_service.application.service.CsMessageService;
import com.fandom.cs_service.presentation.dto.message.UserDeletedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserDeletedConsumer {

    private final CsMessageService csMessageService;

    @KafkaListener(topics = "user.deleted",
            groupId = "${spring.kafka.consumer.group-id}-user-deleted",
            containerFactory = "userDeletedKafkaListenerContainerFactory")
    public void onUserDeleted(UserDeletedMessage message) {
        csMessageService.clearHistory(message.userId());
    }
}
