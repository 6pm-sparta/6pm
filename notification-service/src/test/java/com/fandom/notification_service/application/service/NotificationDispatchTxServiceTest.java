package com.fandom.notification_service.application.service;

import com.fandom.notification_service.application.dto.DeliveryResult;
import com.fandom.notification_service.application.dto.DispatchPlan;
import com.fandom.notification_service.application.port.PushDispatchPort;
import com.fandom.notification_service.domain.entity.DeviceType;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationDelivery;
import com.fandom.notification_service.domain.entity.NotificationSendStatus;
import com.fandom.notification_service.domain.entity.NotificationType;
import com.fandom.notification_service.domain.entity.UserNotificationToken;
import com.fandom.notification_service.domain.repository.NotificationDeliveryRepository;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import com.fandom.notification_service.domain.repository.UserNotificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchTxService 단위 테스트")
class NotificationDispatchTxServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserNotificationTokenRepository tokenRepository;
    @Mock
    private NotificationDeliveryRepository deliveryRepository;
    @Mock
    private PushDispatchPort pushDispatchPort;

    @InjectMocks
    private NotificationDispatchTxService txService;

    private static final UUID NOTI_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(txService, "maxAttempt", 3);
    }

    private Notification notification() {
        return Notification.builder()
                .userId(USER_ID).referenceId(UUID.randomUUID())
                .type(NotificationType.CHAT).title("제목").body("본문").build();
    }

    private UserNotificationToken token(String value) {
        return UserNotificationToken.builder()
                .userId(USER_ID).deviceToken(value).deviceType(DeviceType.WEB).build();
    }

    private NotificationDelivery delivery(String token) {
        return NotificationDelivery.builder()
                .notificationId(NOTI_ID).deviceToken(token).deviceType(DeviceType.WEB).build();
    }

    // ---------- prepare ----------

    @Test
    @DisplayName("prepare: 알림이 없으면 빈 plan, 토큰 조회도 안 한다")
    void prepare_notificationNotFound_empty() {
        given(notificationRepository.findById(NOTI_ID)).willReturn(Optional.empty());

        DispatchPlan plan = txService.prepare(NOTI_ID);

        assertThat(plan.targets()).isEmpty();
        verify(tokenRepository, never()).findAllByUserIdAndNotifiedTrue(any());
    }

    @Test
    @DisplayName("prepare: 보낼 토큰이 없으면 알림을 성공 처리하고 빈 plan")
    void prepare_noTokens_markSuccess() {
        given(notificationRepository.findById(NOTI_ID)).willReturn(Optional.of(notification()));
        given(tokenRepository.findAllByUserIdAndNotifiedTrue(USER_ID)).willReturn(List.of());

        DispatchPlan plan = txService.prepare(NOTI_ID);

        assertThat(plan.targets()).isEmpty();
        verify(notificationRepository).save(any(Notification.class)); // markAsSuccess 저장
    }

    @Test
    @DisplayName("prepare: 신규 토큰은 delivery 생성 + 대상 포함, 이미 성공한 기기는 제외")
    void prepare_newAndAlreadySuccess() {
        UserNotificationToken newToken = token("tokenNew");
        UserNotificationToken doneToken = token("tokenDone");

        NotificationDelivery doneDelivery = delivery("tokenDone");
        doneDelivery.markSuccess(); // 이미 성공

        given(notificationRepository.findById(NOTI_ID)).willReturn(Optional.of(notification()));
        given(tokenRepository.findAllByUserIdAndNotifiedTrue(USER_ID))
                .willReturn(List.of(newToken, doneToken));
        given(deliveryRepository.findAllByNotificationId(NOTI_ID))
                .willReturn(List.of(doneDelivery)); // tokenDone 만 기존 존재

        DispatchPlan plan = txService.prepare(NOTI_ID);

        // 신규 토큰만 대상
        assertThat(plan.targets()).hasSize(1);
        assertThat(plan.targets().get(0).deviceToken()).isEqualTo("tokenNew");
        // 신규 delivery insert
        verify(deliveryRepository).save(any(NotificationDelivery.class));
    }

    // ---------- record ----------

    @Test
    @DisplayName("record: 모두 성공이면 알림을 SUCCESS로 집계한다")
    void record_allSuccess_aggregateSuccess() {
        NotificationDelivery d = delivery("t1");
        Notification n = notification();
        given(deliveryRepository.findByNotificationIdAndDeviceToken(NOTI_ID, "t1"))
                .willReturn(Optional.of(d));
        given(notificationRepository.findById(NOTI_ID)).willReturn(Optional.of(n));
        given(deliveryRepository.findAllByNotificationId(NOTI_ID)).willReturn(List.of(d));

        txService.record(NOTI_ID, List.of(new DeliveryResult("t1", true)));

        assertThat(d.getStatus()).isEqualTo(NotificationSendStatus.SUCCESS);
        assertThat(n.getSendStatus()).isEqualTo(NotificationSendStatus.SUCCESS);
        verify(pushDispatchPort, never()).publishRetry(any(), any());
    }

    @Test
    @DisplayName("record: 실패하면 FAILED 마킹 + 한계 미만이면 재시도 발행")
    void record_fail_publishRetry() {
        NotificationDelivery d = delivery("t1"); // attemptCount 0
        Notification n = notification();
        given(deliveryRepository.findByNotificationIdAndDeviceToken(NOTI_ID, "t1"))
                .willReturn(Optional.of(d));
        given(notificationRepository.findById(NOTI_ID)).willReturn(Optional.of(n));
        given(deliveryRepository.findAllByNotificationId(NOTI_ID)).willReturn(List.of(d));

        txService.record(NOTI_ID, List.of(new DeliveryResult("t1", false)));

        assertThat(d.getStatus()).isEqualTo(NotificationSendStatus.FAILED);
        assertThat(n.getSendStatus()).isEqualTo(NotificationSendStatus.FAILED);

        verify(pushDispatchPort).publishRetry(NOTI_ID, "t1");
    }
}
