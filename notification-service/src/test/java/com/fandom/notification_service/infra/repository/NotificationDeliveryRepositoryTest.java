package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.DeviceType;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationDelivery;
import com.fandom.notification_service.domain.entity.NotificationType;
import com.fandom.notification_service.domain.repository.NotificationDeliveryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(NotificationDeliveryRepositoryImpl.class)
@DisplayName("NotificationDeliveryRepository 커스텀 쿼리 테스트")
class NotificationDeliveryRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private NotificationDeliveryJpaRepository deliveryJpaRepository;
    @Autowired
    private NotificationJpaRepository notificationJpaRepository;
    @Autowired
    private EntityManager em;

    private final UUID USER_A = UUID.randomUUID();
    private final UUID USER_B = UUID.randomUUID();

    private Notification notification(UUID userId) {
        return notificationJpaRepository.saveAndFlush(Notification.builder()
                .userId(userId).referenceId(UUID.randomUUID())
                .type(NotificationType.CHAT).title("제목").body("본문").build());
    }

    private NotificationDelivery delivery(UUID notificationId, String token) {
        return deliveryJpaRepository.saveAndFlush(NotificationDelivery.builder()
                .notificationId(notificationId).deviceToken(token).deviceType(DeviceType.WEB).build());
    }

    @Test
    @DisplayName("findByNotificationIdAndDeviceToken: notificationId+token으로 단건 조회한다")
    void findByNotificationIdAndDeviceToken() {
        Notification n = notification(USER_A);
        delivery(n.getId(), "tokenA");

        assertThat(deliveryRepository.findByNotificationIdAndDeviceToken(n.getId(), "tokenA")).isPresent();
        assertThat(deliveryRepository.findByNotificationIdAndDeviceToken(n.getId(), "없는토큰")).isEmpty();
    }

    @Test
    @DisplayName("deleteByUserId: 서브쿼리로 해당 유저 알림의 전달 레코드만 하드 삭제한다")
    void deleteByUserId_subqueryDeletesOnlyUsersDeliveries() {
        Notification notiA = notification(USER_A);
        Notification notiB = notification(USER_B);
        delivery(notiA.getId(), "a1");
        delivery(notiA.getId(), "a2");
        delivery(notiB.getId(), "b1");

        deliveryRepository.deleteByUserId(USER_A);
        deliveryJpaRepository.flush();

        assertThat(deliveryRepository.findAllByNotificationId(notiA.getId())).isEmpty();
        assertThat(deliveryRepository.findAllByNotificationId(notiB.getId())).hasSize(1);
        // hard delete
        assertThat(countAllDeliveries()).isEqualTo(1);
    }

    private long countAllDeliveries() {
        Object n = em.createNativeQuery("SELECT count(*) FROM notification_deliveries")
                .getSingleResult();
        return ((Number) n).longValue();
    }
}
