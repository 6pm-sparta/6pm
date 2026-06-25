package com.fandom.chat_service.presentation.dto.response;

import com.fandom.chat_service.domain.entity.ChatRoom;

import java.util.UUID;

public record RoomResponse(
        UUID roomId,
        String title
) {
    public static RoomResponse from(ChatRoom room) {
        return new RoomResponse(room.getId(), room.getTitle());
    }
}
