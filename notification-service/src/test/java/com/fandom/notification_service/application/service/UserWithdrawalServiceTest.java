package com.fandom.notification_service.application.service;

import com.fandom.notification_service.domain.repository.NotificationDeliveryRepository;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import com.fandom.notification_service.domain.repository.UserNotificationTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserWithdrawalService 단위 테스트")
class UserWithdrawalServiceTest {

    @Mock
    private UserNotificationTokenRepository tokenRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @InjectMocks
    private UserWithdrawalService withdrawalService;

    @Test
    @DisplayName("탈퇴: deliveries(하드) → notifications(소프트) → tokens(하드) 순서로 정리한다")
    void handle_deletesInOrder() {
        UUID userId = UUID.randomUUID();

        withdrawalService.handle(userId);

        var order = inOrder(deliveryRepository, notificationRepository, tokenRepository);
        order.verify(deliveryRepository).deleteByUserId(userId);
        order.verify(notificationRepository).softDeleteAllByUserId(userId);
        order.verify(tokenRepository).deleteByUserId(userId);
    }
}
