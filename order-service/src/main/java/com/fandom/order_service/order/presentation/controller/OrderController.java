package com.fandom.order_service.order.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.order_service.order.application.OrderQueryService;
import com.fandom.order_service.order.presentation.dto.response.OrderResponse;
import com.fandom.order_service.order.presentation.dto.response.OrderSummaryResponse;
import com.fandom.order_service.order.presentation.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
