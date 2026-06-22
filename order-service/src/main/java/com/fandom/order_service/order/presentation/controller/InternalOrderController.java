package com.fandom.order_service.order.presentation.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.order_service.order.application.creation.OrderCreationResult;
import com.fandom.order_service.order.application.creation.OrderCreationService;
import com.fandom.order_service.order.presentation.dto.request.CreateOrderRequest;
import com.fandom.order_service.order.presentation.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 주문 생성 API. (서비스 간 통신 전용 — Gateway가 수신하지 않아야 함)
 * Ticketing이 좌석 선점 성공 후 Feign으로 호출한다. Client는 직접 호출하지 않는다.
 *
 * /internal/v1 경로는 gateway-service의 라우트 predicate(Path=/api/orders/**)에 걸리지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1")
public class InternalOrderController {

    private final OrderCreationService orderCreationService;

    /**
     * 주문 생성. 신규 생성이면 201, 동일 holdId로 인한 멱등 응답이면 200을 반환한다.
     */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {

        OrderCreationResult result = orderCreationService.createOrder(request);

        if (result.newlyCreated()) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.created(result.order()));
        }

        return ResponseEntity.ok(ApiResponse.success(result.order()));
    }
}
