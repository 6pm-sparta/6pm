package com.fandom.notification_service.application.service;

import com.fandom.common.exception.CustomException;
import com.fandom.notification_service.domain.entity.DeviceType;
import com.fandom.notification_service.domain.entity.UserNotificationToken;
import com.fandom.notification_service.domain.exception.NotificationErrorCode;
import com.fandom.notification_service.domain.repository.UserNotificationTokenRepository;
import com.fandom.notification_service.presentation.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserNotificationTokenService {

    private final UserNotificationTokenRepository tokenRepository;

    // 등록 같은 토큰이면 소유자/타입 갱신
    @Transactional
    public TokenResponse register(UUID userId, String deviceToken, DeviceType deviceType) {
        UserNotificationToken token = tokenRepository.findByDeviceToken(deviceToken)
                .map(existing -> {
                    existing.reassign(userId, deviceType);
                    return existing;
                })
                .orElseGet(() -> tokenRepository.save(UserNotificationToken.builder()
                        .userId(userId)
                        .deviceToken(deviceToken)
                        .deviceType(deviceType)
                        .build()));
        return TokenResponse.from(token);
    }

    // 삭제(로그아웃/기기 해제) - 하드 삭제
    @Transactional
    public void delete(UUID userId, UUID tokenId) {
        UserNotificationToken token = findOwned(userId, tokenId);
        tokenRepository.deleteById(token.getId());
    }

    // 알림 설정 변경
    @Transactional
    public TokenResponse updateSetting(UUID userId, UUID tokenId, boolean isNotified) {
        UserNotificationToken token = findOwned(userId, tokenId);
        token.toggleNotification(isNotified);
        return TokenResponse.from(token);
    }

    // 알림 설정 조회
    @Transactional(readOnly = true)
    public TokenResponse getSetting(UUID userId, UUID tokenId) {
        return TokenResponse.from(findOwned(userId, tokenId));
    }

    // 소유권 검증
    private UserNotificationToken findOwned(UUID userId, UUID tokenId) {
        UserNotificationToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new CustomException(NotificationErrorCode.TOKEN_NOT_FOUND));
        if (!token.getUserId().equals(userId)) {
            throw new CustomException(NotificationErrorCode.TOKEN_NOT_FOUND);
        }
        return token;
    }
}
