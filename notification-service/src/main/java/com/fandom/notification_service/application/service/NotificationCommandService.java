package com.fandom.notification_service.application.service;

import com.fandom.notification_service.application.dto.CreateNotificationCommand;
import com.fandom.notification_service.application.port.PushDispatchPort;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final PushDispatchPort pushDispatchPort;

    public void create(CreateNotificationCommand command) {
        if (command.targetUserIds() == null || command.targetUserIds().isEmpty()) {
            log.warn("notification.send: target_user_ids 비어있음. reference_id={}", command.referenceId());
            return;
        }
        for (UUID userId : command.targetUserIds()) {
            saveIfAbsent(userId, command);
        }
    }

    private void saveIfAbsent(UUID userId, CreateNotificationCommand command) {
        if (notificationRepository.existsByUserIdAndTypeAndReferenceId( // 멱등성 확인
                userId, command.type(), command.referenceId())) {
            return;
        }
        try {
            Notification saved = notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .referenceId(command.referenceId())
                    .type(command.type())
                    .title(command.title())
                    .body(command.content())
                    .build());
            pushDispatchPort.dispatch(saved.getId(), userId); // 발송 트리거 발행
        } catch (DataIntegrityViolationException e) {
            log.debug("중복 알림 스킵 user_id={}, type={}, reference_id={}",
                    userId, command.type(), command.referenceId());
        }
    }
}
