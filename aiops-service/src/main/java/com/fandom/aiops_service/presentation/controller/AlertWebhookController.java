package com.fandom.aiops_service.presentation.controller;

import com.fandom.aiops_service.application.AlertWebhookService;
import com.fandom.aiops_service.presentation.dto.request.AlertWebhookRequest;
import com.fandom.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alertmanager 웹훅 수신 엔드포인트.
 * alertmanager.yml 의 webhook_configs.url = http://aiops-service:8086/api/v1/alerts/webhook
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alerts")
public class AlertWebhookController {

    private final AlertWebhookService alertWebhookService;

    @PostMapping("/webhook")
    public ApiResponse<Void> receive(@RequestBody AlertWebhookRequest request) {
        alertWebhookService.handleWebhook(request);
        return ApiResponse.success();   // 200 OK — Alertmanager 재전송 방지
    }
}
