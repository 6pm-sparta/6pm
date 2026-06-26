package com.fandom.chat_service.presentation.controller;

import com.fandom.chat_service.application.service.ChatMessageService;
import com.fandom.chat_service.presentation.dto.request.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/rooms/{roomId}/messages")
    public void send(@DestinationVariable UUID roomId, SendMessageRequest request, Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        chatMessageService.send(roomId, senderId, request.content());
    }
}
