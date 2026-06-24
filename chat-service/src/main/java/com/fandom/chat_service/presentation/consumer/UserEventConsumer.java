package com.fandom.chat_service.presentation.consumer;

import com.fandom.chat_service.application.service.ChatRoomCommandService;
import com.fandom.chat_service.infra.kafka.KafkaTopics;
import com.fandom.chat_service.presentation.dto.message.CreatorCreatedMessage;
import com.fandom.chat_service.presentation.dto.message.FollowEventMessage;
import com.fandom.chat_service.presentation.dto.message.UserDeletedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final ChatRoomCommandService chatRoomCommandService;

    @KafkaListener(topics = KafkaTopics.USER_CREATOR_CREATED,
            groupId = "${spring.kafka.consumer.group-id}-creator-created",
            containerFactory = "creatorCreatedKafkaListenerContainerFactory")
    public void onCreatorCreated(CreatorCreatedMessage m) {
        chatRoomCommandService.handleCreatorCreated(m.userId(), m.nickname());
    }

    @KafkaListener(topics = KafkaTopics.USER_FOLLOWED,
            groupId = "${spring.kafka.consumer.group-id}-followed",
            containerFactory = "followEventKafkaListenerContainerFactory")
    public void onFollowed(FollowEventMessage m) {
        chatRoomCommandService.handleFollowed(m.followeeId(), m.followerId());
    }

    @KafkaListener(topics = KafkaTopics.USER_UNFOLLOWED,
            groupId = "${spring.kafka.consumer.group-id}-unfollowed",
            containerFactory = "followEventKafkaListenerContainerFactory")
    public void onUnfollowed(FollowEventMessage m) {
        chatRoomCommandService.handleUnfollowed(m.followeeId(), m.followerId());
    }

    @KafkaListener(topics = KafkaTopics.USER_DELETED,
            groupId = "${spring.kafka.consumer.group-id}-user-deleted",
            containerFactory = "userDeletedKafkaListenerContainerFactory")
    public void onUserDeleted(UserDeletedMessage m) {
        chatRoomCommandService.handleUserDeleted(m.userId());
    }
}
