package com.fandom.chat_service.presentation.controller;

import com.fandom.chat_service.application.service.ChatMessageService;
import com.fandom.chat_service.presentation.dto.request.SendMessageRequest;
import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import com.fandom.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/rooms/{roomId}/messages")
    public void send(@DestinationVariable UUID roomId, SendMessageRequest request, Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        MDC.put("roomId", roomId.toString());
        MDC.put("userId", senderId.toString());
        try {
            chatMessageService.send(roomId, senderId, request.content());
        } finally {
            MDC.clear();
        }
    }

    // 클라이언트 개인 에러
    @MessageExceptionHandler(CustomException.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ApiResponse.error(errorCode.getStatus(), errorCode.getMessage());
    }

    // 일반 오류 응답
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleException(Exception e) {
        log.error("STOMP 메시지 처리 실패", e);
        return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, "메시지 처리 중 오류가 발생했습니다.");
    }
}
