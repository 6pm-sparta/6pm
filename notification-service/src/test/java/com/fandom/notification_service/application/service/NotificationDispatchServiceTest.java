package com.fandom.notification_service.application.service;

import com.fandom.notification_service.application.dto.DeliveryResult;
import com.fandom.notification_service.application.dto.DeliveryTarget;
import com.fandom.notification_service.application.dto.DispatchPlan;
import com.fandom.notification_service.application.port.NotificationSender;
import com.fandom.notification_service.domain.entity.DeviceType;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchService 단위 테스트")
class NotificationDispatchServiceTest {

    @Mock
    private NotificationDispatchTxService txService;
    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private NotificationDispatchService dispatchService;

    private static final UUID NOTI_ID = UUID.randomUUID();

    @Test
    @DisplayName("대상이 없으면 발송도 기록도 하지 않는다")
    void dispatch_emptyPlan_noop() {
        given(txService.prepare(NOTI_ID)).willReturn(DispatchPlan.empty());

        dispatchService.dispatch(NOTI_ID);

        verify(notificationSender, never()).send(any(), any(), any(), any());
        verify(txService, never()).record(any(), anyList());
    }

    @Test
    @DisplayName("대상마다 발송하고 결과를 record로 넘긴다 (일부 실패 시 success=false)")
    void dispatch_sendsAndRecords() {
        DispatchPlan plan = new DispatchPlan("제목", "본문", List.of(
                new DeliveryTarget("ok", DeviceType.WEB),
                new DeliveryTarget("fail", DeviceType.WEB)));
        given(txService.prepare(NOTI_ID)).willReturn(plan);

        lenient().doThrow(new IllegalStateException("boom"))
                .when(notificationSender).send("fail", DeviceType.WEB, "제목", "본문");

        dispatchService.dispatch(NOTI_ID);

        verify(notificationSender).send("ok", DeviceType.WEB, "제목", "본문");
        verify(notificationSender).send("fail", DeviceType.WEB, "제목", "본문");

        ArgumentCaptor<List<DeliveryResult>> captor = ArgumentCaptor.forClass(List.class);
        verify(txService).record(eq(NOTI_ID), captor.capture());
        List<DeliveryResult> results = captor.getValue();
        assertThat(results).containsExactlyInAnyOrder(
                new DeliveryResult("ok", true),
                new DeliveryResult("fail", false));
    }

    @Test
    @DisplayName("재발송: plan이 비어있으면 발송/기록 안 함")
    void retry_emptyPlan_noop() {
        given(txService.prepareRetry(NOTI_ID, "t1")).willReturn(DispatchPlan.empty());

        dispatchService.retry(NOTI_ID, "t1");

        verify(notificationSender, never()).send(any(), any(), any(), any());
        verify(txService, never()).recordRetry(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("재발송: 성공하면 recordRetry(true)")
    void retry_success() {
        DispatchPlan plan = new DispatchPlan("제목", "본문",
                List.of(new DeliveryTarget("t1", DeviceType.WEB)));
        given(txService.prepareRetry(NOTI_ID, "t1")).willReturn(plan);

        dispatchService.retry(NOTI_ID, "t1");

        verify(notificationSender).send("t1", DeviceType.WEB, "제목", "본문");
        verify(txService).recordRetry(NOTI_ID, "t1", true);
    }
}
