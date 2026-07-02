package com.fandom.notification_service.infra.repository;

import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationType;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(NotificationRepositoryImpl.class)
@DisplayName("NotificationRepository 커스텀 쿼리 테스트")
class NotificationRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationJpaRepository jpaRepository;
    @Autowired
    private EntityManager em;

    private final UUID USER_A = UUID.randomUUID();
    private final UUID USER_B = UUID.randomUUID();
    private final UUID REF = UUID.randomUUID();

    private Notification save(UUID userId, UUID refId, NotificationType type) {
        return jpaRepository.saveAndFlush(Notification.builder()
                .userId(userId).referenceId(refId).type(type).title("제목").body("본문").build());
    }

    @Test
    @DisplayName("findExistingUserIds: referenceId+type가 일치하는 유저만 반환한다(멱등성)")
    void findExistingUserIds_filtersByRefTypeUser() {
        save(USER_A, REF, NotificationType.CHAT);
        save(USER_B, REF, NotificationType.CHAT);
        save(USER_A, REF, NotificationType.FEED_NEW_POST);   // 타입 다름
        save(USER_A, UUID.randomUUID(), NotificationType.CHAT); // ref 다름

        UUID userC = UUID.randomUUID();
        List<UUID> existing = notificationRepository.findExistingUserIds(
                REF, NotificationType.CHAT, List.of(USER_A, USER_B, userC));

        assertThat(existing).containsExactlyInAnyOrder(USER_A, USER_B);
    }

    @Test
    @DisplayName("softDeleteAllByUserId: 해당 유저 알림만 조회에서 사라지고 행은 남는다")
    void softDeleteAllByUserId() {
        save(USER_A, UUID.randomUUID(), NotificationType.CHAT);
        save(USER_A, UUID.randomUUID(), NotificationType.CHAT);
        save(USER_B, UUID.randomUUID(), NotificationType.CHAT);

        notificationRepository.softDeleteAllByUserId(USER_A);
        jpaRepository.flush();

        assertThat(notificationRepository.findInbox(USER_A, null, 10)).isEmpty();
        assertThat(notificationRepository.findInbox(USER_B, null, 10)).hasSize(1);
        // softDelete
        assertThat(countSoftDeleted(USER_A)).isEqualTo(2);
    }

    @Test
    @DisplayName("findInbox: 최신순 + limit, 커서 이후만 가져온다")
    void findInbox_cursorPaging() {
        save(USER_A, UUID.randomUUID(), NotificationType.CHAT);
        Notification n2 = save(USER_A, UUID.randomUUID(), NotificationType.CHAT);
        Notification n3 = save(USER_A, UUID.randomUUID(), NotificationType.CHAT);

        List<Notification> firstPage = notificationRepository.findInbox(USER_A, null, 2);
        assertThat(firstPage).extracting(Notification::getId).containsExactly(n3.getId(), n2.getId());

        List<Notification> afterN2 = notificationRepository.findInbox(USER_A, n2.getId(), 10);
        assertThat(afterN2).hasSize(1); // n1 만 남음
    }

    @Test
    @DisplayName("findPendingCreatedBefore: cutoff 이전의 PENDING만 가져온다")
    void findPendingCreatedBefore_oldPendingOnly() {
        LocalDateTime old = LocalDateTime.now().minusMinutes(10);
        LocalDateTime fresh = LocalDateTime.now();

        Notification oldPending = saveAt(old, false);
        saveAt(fresh, false);      // 최신 PENDING
        saveAt(old, true);         // 오래됐지만 SUCCESS

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        List<Notification> result = notificationRepository.findPendingCreatedBefore(cutoff, 100);

        assertThat(result).extracting(Notification::getId).containsExactly(oldPending.getId());
    }

    private Notification saveAt(LocalDateTime createdAt, boolean success) {
        Notification n = Notification.builder()
                .userId(USER_A).referenceId(UUID.randomUUID())
                .type(NotificationType.CHAT).title("제목").body("본문").build();
        if (success) {
            n.markAsSuccess();
        }
        ReflectionTestUtils.setField(n, "createdAt", createdAt);
        return jpaRepository.saveAndFlush(n);
    }

    private long countSoftDeleted(UUID userId) {
        Object n = em.createNativeQuery(
                        "SELECT count(*) FROM notifications WHERE user_id = :uid AND deleted_at IS NOT NULL")
                .setParameter("uid", userId)
                .getSingleResult();
        return ((Number) n).longValue();
    }
}
