package com.fandom.order_service.payment.presentation.dto.request;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 결제 요청. api 명세서 "결제 요청" 섹션 기준.
 * Idempotency-Key는 본문이 아니라 헤더로 전달받는다.
 */
public record PaymentRequest(

        @NotNull(message = "orderId는 필수입니다.")
        UUID orderId,

        @NotNull(message = "paymentMethod는 필수입니다.")
        PaymentMethod paymentMethod
) {
}
