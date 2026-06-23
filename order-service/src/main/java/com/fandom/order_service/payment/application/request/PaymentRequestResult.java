package com.fandom.order_service.payment.application.request;

import com.fandom.order_service.payment.presentation.dto.response.PaymentResponse;

/**
 * 신규 결제 처리(201)인지 멱등 응답(200, 캐시된 기존 결과 반환)인지 컨트롤러가 알아야
 * HTTP 상태코드를 결정할 수 있다 (OrderCreationResult와 동일한 패턴).
 */
public record PaymentRequestResult(
        PaymentResponse payment,
        boolean newlyProcessed
) {
}
