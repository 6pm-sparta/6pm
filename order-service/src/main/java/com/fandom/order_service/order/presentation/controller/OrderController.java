package com.fandom.order_service.order.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.order_service.order.application.cancellation.OrderCancelService;
import com.fandom.order_service.order.application.query.OrderQueryService;
import com.fandom.order_service.order.presentation.dto.response.OrderCancelResponse;
import com.fandom.order_service.order.presentation.dto.response.OrderResponse;
import com.fandom.order_service.order.presentation.dto.response.OrderSummaryResponse;
import com.fandom.order_service.order.presentation.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 주문 조회 API. (외부 - Gateway 경유)
 * 단건/목록 모두 @CurrentIdCard로 주입받은 userId 기준으로 본인 주문만 접근 가능하다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;

    /**
     * 주문 단건 조회. 본인 주문이 아니면 403, 존재하지 않으면 404.
     */
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> getOrder(
            @PathVariable("id") UUID orderId,
            @CurrentIdCard UserIdCard idCard) {

        OrderResponse response = orderQueryService.getOrder(orderId, idCard.getUserId());

        return ApiResponse.success(response);
    }

    /**
     * 본인의 전체 주문 목록 조회. page/size 기본값 0/20.
     */
    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderSummaryResponse>> getOrders(
            @CurrentIdCard UserIdCard idCard,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<OrderSummaryResponse> response = orderQueryService.getOrders(idCard.getUserId(), page, size);

        return ApiResponse.success(response);
    }

    /**
     * 주문 취소. PENDING은 즉시 CANCELLED, PAID/CONFIRMED(취소 가능 시간 내)는 PG 환불까지
     * 동기 처리 후 최종 상태(REFUNDED)로 응답한다. 이미 CANCELLED/REFUNDED인 주문은 멱등 응답
     * (200 + 현재 상태 그대로).
     */
    @DeleteMapping("/orders/{id}")
    public ApiResponse<OrderCancelResponse> cancelOrder(
            @PathVariable("id") UUID orderId,
            @CurrentIdCard UserIdCard idCard) {

        OrderCancelResponse response = orderCancelService.cancelOrder(orderId, idCard.getUserId());

        return ApiResponse.success(response);
    }
}
