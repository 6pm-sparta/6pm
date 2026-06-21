package com.fandom.notification_service.application.service;

import com.fandom.common.exception.CustomException;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.exception.NotificationErrorCode;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import com.fandom.notification_service.presentation.dto.response.InboxResponse;
import com.fandom.notification_service.presentation.dto.response.NotificationItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private static final int MAX_SIZE = 100;

    private final NotificationRepository notificationRepository;

    // 보관함 조회
    @Transactional(readOnly = true)
    public InboxResponse getInbox(UUID userId, UUID cursor, int size) {
        int limit = Math.clamp(size, 1, MAX_SIZE);
        List<Notification> rows = notificationRepository.findInbox(userId, cursor, limit + 1);

        boolean hasNext = rows.size() > limit;
        List<Notification> page = hasNext ? rows.subList(0, limit) : rows;

        List<NotificationItemResponse> noties = page.stream()
                .map(NotificationItemResponse::from)
                .toList();
        UUID nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new InboxResponse(noties, nextCursor, hasNext);
    }

    // 읽음 처리
    @Transactional
    public NotificationItemResponse markRead(UUID userId, UUID notificationId) {
        Notification n = findOwned(userId, notificationId);
        n.markAsRead();
        return NotificationItemResponse.from(n);
    }

    // 보관함 비우기 - 소프트 삭제
    @Transactional
    public void clearAll(UUID userId) {
        notificationRepository.softDeleteAllByUserId(userId);
    }

    // 소유권 검증
    private Notification findOwned(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!n.getUserId().equals(userId)) {
            throw new CustomException(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
        return n;
    }
}
