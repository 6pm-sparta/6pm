package com.fandom.chat_service.application.service;

import com.fandom.chat_service.domain.entity.ChatRoomMember;
import com.fandom.chat_service.domain.repository.ChatRoomMemberRepository;
import com.fandom.chat_service.domain.repository.ChatRoomRepository;
import com.fandom.chat_service.presentation.dto.response.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRoomQueryService {

    private final ChatRoomMemberRepository memberRepository;
    private final ChatRoomRepository roomRepository;

    // 참여 중인 방 목록
    @Transactional(readOnly = true)
    public List<RoomResponse> getMyRooms(UUID userId) {
        List<UUID> roomIds = memberRepository.findAllByUserId(userId).stream()
                .map(ChatRoomMember::getRoomId)
                .toList();
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return roomRepository.findAllByIdIn(roomIds).stream()
                .map(RoomResponse::from)
                .toList();
    }
}
