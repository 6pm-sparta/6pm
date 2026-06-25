package com.fandom.chat_service.presentation.controller;

import com.fandom.chat_service.application.service.ChatMessageService;
import com.fandom.chat_service.application.service.ChatRoomQueryService;
import com.fandom.chat_service.presentation.dto.request.SendMessageRequest;
import com.fandom.chat_service.presentation.dto.response.MessageListResponse;
import com.fandom.chat_service.presentation.dto.response.MessageResponse;
import com.fandom.chat_service.presentation.dto.response.RoomResponse;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomQueryService chatRoomQueryService;

    // 메시지 전송
    @PostMapping("/rooms/{roomId}/messages")
    public ApiResponse<MessageResponse> send(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ApiResponse.success(
                chatMessageService.send(roomId, idCard.getUserId(), request.content()));
    }

    // 채팅 내역 조회
    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<MessageListResponse> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(required = false) Integer size,
            @CurrentIdCard UserIdCard idCard
    ) {
        return ApiResponse.success(
                chatMessageService.getMessages(roomId, idCard.getUserId(), cursor, size));
    }

    // 참여 중인 방 목록
    @GetMapping("/rooms")
    public ApiResponse<List<RoomResponse>> getMyRooms(@CurrentIdCard UserIdCard idCard) {
        return ApiResponse.success(chatRoomQueryService.getMyRooms(idCard.getUserId()));
    }
}
