package com.fandom.notification_service.application.service;

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

    // 회원 탈퇴 - 토큰 삭제 + 알림 삭제
    // bulk 삭제되는 알림 삭제 뒤에 토큰 삭제 해야 삭제 유실 없음.
    @Transactional
    public void handle(UUID userId) {
        notificationRepository.softDeleteAllByUserId(userId);
        tokenRepository.deleteByUserId(userId);
        log.info("회원 탈퇴 처리 완료 user_id={}", userId);
    }
}
