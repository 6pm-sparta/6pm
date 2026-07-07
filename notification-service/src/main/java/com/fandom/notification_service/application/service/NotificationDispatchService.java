package com.fandom.notification_service.application.service;

import com.fandom.notification_service.application.dto.DeliveryResult;
import com.fandom.notification_service.application.dto.DeliveryTarget;
import com.fandom.notification_service.application.dto.DispatchPlan;
import com.fandom.notification_service.application.port.NotificationSender;
import com.fandom.notification_service.support.LogMask;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private static final String RESULT_METRIC = "notification.push.result"; // {result=success|fail}

    private final NotificationDispatchTxService txService;
    private final NotificationSender notificationSender;
    private final MeterRegistry meterRegistry;

    // 발송
    public void dispatch(UUID notificationId) {
        DispatchPlan plan = txService.prepare(notificationId);
        if (plan.targets().isEmpty()) {
            return;
        }
        List<DeliveryResult> results = new ArrayList<>();
        for (DeliveryTarget target : plan.targets()) {
            results.add(sendOne(notificationId, target, plan.title(), plan.body()));
        }
        txService.record(notificationId, results);
    }

    // 재발송
    public void retry(UUID notificationId, String deviceToken) {
        DispatchPlan plan = txService.prepareRetry(notificationId, deviceToken);
        if (plan.targets().isEmpty()) {
            return;
        }
        DeliveryTarget target = plan.targets().get(0);
        boolean success = trySend(notificationId, target.deviceToken(), target.deviceType(), plan.title(), plan.body());
        txService.recordRetry(notificationId, deviceToken, success);
    }

    private DeliveryResult sendOne(UUID notificationId, DeliveryTarget target, String title, String body) {
        boolean success = trySend(notificationId, target.deviceToken(), target.deviceType(), title, body);
        return new DeliveryResult(target.deviceToken(), success);
    }

    private boolean trySend(UUID notificationId, String deviceToken,
                            com.fandom.notification_service.domain.entity.DeviceType deviceType,
                            String title, String body) {
        try {
            notificationSender.send(deviceToken, deviceType, title, body);
            meterRegistry.counter(RESULT_METRIC, "result", "success").increment();
            return true;
        } catch (Exception e) {
            meterRegistry.counter(RESULT_METRIC, "result", "fail").increment();
            log.error("기기 발송 실패 id={}, token={}", notificationId, LogMask.token(deviceToken), e);
            return false;
        }
    }
}
