package com.fandom.notification_service.application.service;

import com.fandom.notification_service.application.dto.CreateNotificationCommand;
import com.fandom.notification_service.application.port.PushDispatchPort;
import com.fandom.notification_service.domain.entity.Notification;
import com.fandom.notification_service.domain.entity.NotificationType;
import com.fandom.notification_service.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCommandService 단위 테스트")
class NotificationCommandServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PushDispatchPort pushDispatchPort;

    @InjectMocks
    private NotificationCommandService commandService;

    private static final UUID REF_ID = UUID.randomUUID();

    private CreateNotificationCommand command(List<UUID> targets) {
        return new CreateNotificationCommand(REF_ID, NotificationType.CHAT, "제목", "본문", targets);
    }

    @Test
    @DisplayName("target_user_ids 가 비어있으면 아무 것도 하지 않는다")
    void create_emptyTargets_noop() {
        commandService.create(command(List.of()));

        verify(notificationRepository, never()).findExistingUserIds(any(), any(), any());
        verify(notificationRepository, never()).saveAll(anyList());
        verify(pushDispatchPort, never()).dispatch(any(), any());
    }

    @Test
    @DisplayName("이미 알림이 있는 유저만 있으면(멱등) 저장하지 않는다")
    void create_allExisting_skip() {
        UUID u1 = UUID.randomUUID();
        given(notificationRepository.findExistingUserIds(REF_ID, NotificationType.CHAT, List.of(u1)))
                .willReturn(List.of(u1)); // 이미 존재

        commandService.create(command(List.of(u1)));

        verify(notificationRepository, never()).saveAll(anyList());
        verify(pushDispatchPort, never()).dispatch(any(), any());
    }

    @Test
    @DisplayName("이미 같은 알림이 등록된 유저는 제외하고, 미등록 유저만 저장·발송한다")
    void create_onlyUnregisteredUsers_bulkSaveAndDispatch() {
        UUID registered = UUID.randomUUID();   // 이미 이 알림(reference_id+type) 보유
        UUID unregistered = UUID.randomUUID(); // 아직 미등록
        List<UUID> targets = List.of(registered, unregistered);

        given(notificationRepository.findExistingUserIds(REF_ID, NotificationType.CHAT, targets))
                .willReturn(List.of(registered)); // 멱등성: registered 는 이미 존재
        given(notificationRepository.saveAll(anyList()))
                .willAnswer(inv -> inv.getArgument(0)); // 저장 결과 = 입력 그대로

        commandService.create(command(targets));

        // 미등록 유저 1건만 저장 대상
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        List<Notification> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getUserId()).isEqualTo(unregistered);
        assertThat(saved.getFirst().getReferenceId()).isEqualTo(REF_ID);

        // 저장된 건수만큼 발송 트리거
        verify(pushDispatchPort, times(1)).dispatch(any(), any());
    }
}
