package com.fandom.chat_service.application.service;

import com.fandom.chat_service.application.port.ChatNotificationPort;
import com.fandom.chat_service.application.port.OnlineStatePort;
import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.entity.SenderRole;
import com.fandom.chat_service.presentation.dto.response.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDeliveryService {

    private static final String USER_QUEUE = "/queue/messages";
    private static final String ROOM_TOPIC_PREFIX = "/topic/room.";

    private final SimpMessagingTemplate messagingTemplate;
    private final OnlineStatePort onlineState;
    private final RoomMemberCacheService roomMemberCache;
    private final ChatNotificationPort chatNotificationPort;

    public void deliver(ChatRoom room, MessageResponse message) {
        try {
            if (message.senderRole() == SenderRole.CREATOR) {
                deliverBroadcast(room, message);
            } else {
                sendToUser(message.senderId(), message);
                deliverReplyToCreator(room, message);
            }
        } catch (Exception e) {
            log.error("메시지 전달 실패 room_id={}, message_id={}", room.getId(), message.id(), e);
        }
    }

    // 크리에이터 메시지 - 온라인은 방 토픽 1회 발행, 오프라인은 알림
    private void deliverBroadcast(ChatRoom room, MessageResponse message) {
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + room.getId(), message);

        Set<UUID> fans = roomMemberCache.getFans(room.getId(), room.getCreatorId());
        if (fans.isEmpty()) {
            return;
        }
        Set<UUID> online = new HashSet<>(onlineState.filterOnline(fans));
        List<UUID> offline = fans.stream().filter(fan -> !online.contains(fan)).toList();
        if (!offline.isEmpty()) { // offline 처리
            chatNotificationPort.notifyNewMessage(message.id(), room.getTitle(), message.content(), offline);
        }
        log.info("broadcast room_id={}, online={}, offline={}", room.getId(), online.size(), offline.size());
    }

    // 팬 답장
    private void deliverReplyToCreator(ChatRoom room, MessageResponse message) {
        UUID creatorId = room.getCreatorId();
        if (onlineState.isOnline(creatorId)) {
            sendToUser(creatorId, message);
        }
    }

    private void sendToUser(UUID userId, MessageResponse message) {
        messagingTemplate.convertAndSendToUser(userId.toString(), USER_QUEUE, message);
    }
}
