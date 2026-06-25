package com.fandom.chat_service.application.service;

import com.fandom.chat_service.domain.entity.ChatMessage;
import com.fandom.chat_service.domain.entity.ChatRoom;
import com.fandom.chat_service.domain.entity.ChatRoomMember;
import com.fandom.chat_service.domain.entity.SenderRole;
import com.fandom.chat_service.domain.exception.ChatErrorCode;
import com.fandom.chat_service.domain.repository.ChatMessageRepository;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import com.fandom.chat_service.domain.repository.ChatRoomRepository;
import com.fandom.chat_service.presentation.dto.response.MessageListResponse;
import com.fandom.chat_service.presentation.dto.response.MessageResponse;
import com.fandom.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int MAX_SIZE = 100;
    private static final int DEFAULT_SIZE = 20;

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;

    // 메시지 전송
    @Transactional
    public MessageResponse send(UUID roomId, UUID senderId, String content) {
        ChatRoom room = getRoomOrThrow(roomId);
        // 멤버십 검증 + 닉네임
        ChatRoomMember member = memberRepository.findByRoomIdAndUserId(roomId, senderId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.CHAT_ACCESS_DENIED));

        SenderRole role = room.getCreatorId().equals(senderId) ? SenderRole.CREATOR : SenderRole.MEMBER;

        ChatMessage saved = messageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .senderRole(role)
                .senderNickname(member.getNickname())
                .content(content)
                .build());
        log.info("메시지 저장 room_id={}, sender_id={}, role={}", roomId, senderId, role);
        return MessageResponse.from(saved);
    }

    // 채팅 내역 조회
    @Transactional(readOnly = true)
    public MessageListResponse getMessages(UUID roomId, UUID requesterId, UUID cursor, Integer size) {
        ChatRoom room = getRoomOrThrow(roomId);
        requireMember(roomId, requesterId);

        int limit = Math.clamp(size == null ? DEFAULT_SIZE : size, 1, MAX_SIZE);
        boolean isCreator = room.getCreatorId().equals(requesterId);

        // hasNext
        List<ChatMessage> fetched = isCreator
                ? messageRepository.findMessages(roomId, cursor, limit + 1)
                : messageRepository.findMessagesForFan(roomId, requesterId, cursor, limit + 1);

        boolean hasNext = fetched.size() > limit;
        List<ChatMessage> page = hasNext ? fetched.subList(0, limit) : fetched;
        UUID nextCursor = page.isEmpty() ? null : page.getLast().getId();

        List<MessageResponse> items = page.stream().map(MessageResponse::from).toList();
        return new MessageListResponse(items, nextCursor, hasNext);
    }

    private ChatRoom getRoomOrThrow(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ChatErrorCode.ROOM_NOT_FOUND));
    }

    private void requireMember(UUID roomId, UUID userId) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new CustomException(ChatErrorCode.CHAT_ACCESS_DENIED);
        }
    }
}
