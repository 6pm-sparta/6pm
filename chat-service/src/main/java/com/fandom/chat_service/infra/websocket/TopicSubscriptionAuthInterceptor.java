package com.fandom.chat_service.infra.websocket;

import com.fandom.chat_service.domain.exception.ChatErrorCode;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import com.fandom.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TopicSubscriptionAuthInterceptor implements ChannelInterceptor { // /topic/room.{roomId} 구독 시 방 멤버십을 검증

    private static final String ROOM_TOPIC_PREFIX = "/topic/room.";

    private final ChatRoomMemberRepository memberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return message; // 방 토픽 외는 통과
        }

        UUID roomId = parseRoomId(destination);
        UUID userId = resolveUserId(accessor.getUser());
        if (userId == null || !memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new CustomException(ChatErrorCode.CHAT_ACCESS_DENIED);
        }
        return message;
    }

    private UUID parseRoomId(String destination) {
        try {
            return UUID.fromString(destination.substring(ROOM_TOPIC_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ChatErrorCode.ROOM_NOT_FOUND);
        }
    }

    private UUID resolveUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
