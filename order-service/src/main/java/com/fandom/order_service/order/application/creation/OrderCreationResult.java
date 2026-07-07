package com.fandom.order_service.order.application.creation;

import com.fandom.order_service.order.presentation.dto.response.OrderResponse;

/**
 * 신규 생성(201)인지 멱등 응답(200, 기존 주문 반환)인지 컨트롤러가 알아야 HTTP 상태코드를 결정할 수 있다.
 */
public record OrderCreationResult(
        OrderResponse order,
        boolean newlyCreated
) {
}
