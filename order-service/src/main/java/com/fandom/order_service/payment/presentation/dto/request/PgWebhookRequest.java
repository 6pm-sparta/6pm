package com.fandom.order_service.payment.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * PG 콜백(Webhook) 수신 요청.
 */
public record PgWebhookRequest(

        @NotBlank(message = "pgTransactionId는 필수입니다.")
        String pgTransactionId,

        @NotNull(message = "orderId는 필수입니다.")
        UUID orderId,

        @NotBlank(message = "status는 필수입니다.")
        String status,

        @NotNull(message = "amount는 필수입니다.")
        @Positive(message = "amount는 양수여야 합니다.")
        Long amount,

        String failureReason
) {
}
