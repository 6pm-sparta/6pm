package com.fandom.notification_service.application.service;

import com.fandom.notification_service.domain.repository.NotificationDeliveryRepository;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import com.fandom.notification_service.domain.repository.UserNotificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserNotificationTokenRepository tokenRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    // 회원 탈퇴 - 전달레코드 + 알림 + 토큰
    @Transactional
    public void handle(UUID userId) {
        deliveryRepository.deleteByUserId(userId);
        notificationRepository.softDeleteAllByUserId(userId);
        tokenRepository.deleteByUserId(userId);
        log.info("회원 탈퇴 처리 완료 user_id={}", userId);
    }
}
