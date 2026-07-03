package com.fandom.cs_service.application.service;

import com.fandom.cs_service.application.port.CsAnswerPort;
import com.fandom.cs_service.domain.entity.CsMessage;
import com.fandom.cs_service.domain.entity.SenderRole;
import com.fandom.cs_service.domain.repository.CsMessageRepository;
import com.fandom.cs_service.presentation.dto.response.CsMessageListResponse;
import com.fandom.cs_service.presentation.dto.response.CsMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsMessageService {

    private static final int MAX_SIZE = 100;
    private static final int DEFAULT_SIZE = 20;

    private final CsMessageRepository messageRepository;
    private final CsAnswerPort answerPort;

    public String inquire(UUID userId, String question) {
        messageRepository.save(CsMessage.builder()
                .userId(userId).senderRole(SenderRole.USER).content(question).build());

        String answer = answerPort.generateAnswer(question); // 답변 호출

        messageRepository.save(CsMessage.builder()
                .userId(userId).senderRole(SenderRole.AI).content(answer).build());

        log.info("cs 문의 처리 user_id={}", userId);
        return answer;
    }

    // 커서
    @Transactional(readOnly = true)
    public CsMessageListResponse getHistory(UUID userId, UUID cursor, Integer size) {
        int limit = Math.clamp(size == null ? DEFAULT_SIZE : size, 1, MAX_SIZE);

        List<CsMessage> fetched = messageRepository.findMessages(userId, cursor, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<CsMessage> page = hasNext ? fetched.subList(0, limit) : fetched;
        UUID nextCursor = page.isEmpty() ? null : page.getLast().getId();

        List<CsMessageResponse> items = page.stream().map(CsMessageResponse::from).toList();
        return new CsMessageListResponse(items, nextCursor, hasNext);
    }

    // 문의 초기화
    @Transactional
    public void clearHistory(UUID userId) {
        messageRepository.softDeleteAllByUserId(userId);
        log.info("cs 문의 내역 초기화 user_id={}", userId);
    }
}
