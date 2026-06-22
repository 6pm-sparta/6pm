package com.fandom.order_service.payment.presentation.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.order_service.payment.application.webhook.PgWebhookService;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PG 콜백(Webhook) 수신 API.
 *
 * Authorization Bearer Token이 아니라 X-PG-Signature HMAC 서명으로 호출 주체를 검증한다
 * (PG사 → order-service 직접 호출이라 사용자 인증 흐름과 무관함).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/webhooks")
public class PgWebhookController {

    private final PgWebhookService pgWebhookService;

    @PostMapping("/payments")
    public ApiResponse<Void> receivePaymentWebhook(
            @Valid @RequestBody PgWebhookRequest request,
            @RequestHeader("X-PG-Signature") String signature) {

        pgWebhookService.receive(request, signature);
        return ApiResponse.success();
    }
}
