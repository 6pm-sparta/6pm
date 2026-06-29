package com.fandom.notification_service.application.service;

import com.fandom.notification_service.application.dto.CreateNotificationCommand;
import com.fandom.notification_service.application.port.PushDispatchPort;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final PushDispatchPort pushDispatchPort;

    @Transactional
    public void create(CreateNotificationCommand command) {
        if (command.targetUserIds() == null || command.targetUserIds().isEmpty()) {
            log.warn("notification.send: target_user_ids 비어있음. reference_id={}", command.referenceId());
            return;
        }

        // 멱등성
        Set<UUID> existing = new HashSet<>(notificationRepository.findExistingUserIds(
                command.referenceId(), command.type(), command.targetUserIds()));
        List<UUID> newUserIds = command.targetUserIds().stream()
                .distinct()
                .filter(userId -> !existing.contains(userId))
                .toList();
        if (newUserIds.isEmpty()) {
            return;
        }

        // 벌크 insert
        List<Notification> saved = notificationRepository.saveAll(
                newUserIds.stream()
                        .map(userId -> Notification.builder()
                                .userId(userId)
                                .referenceId(command.referenceId())
                                .type(command.type())
                                .title(command.title())
                                .body(command.content())
                                .build())
                        .toList());

        // 발송 트리거 발행
        saved.forEach(n -> pushDispatchPort.dispatch(n.getId(), n.getUserId()));
        log.info("notification 생성 reference_id={}, type={}, saved={}, skipped={}",
                command.referenceId(), command.type(), saved.size(), existing.size());
    }
}
