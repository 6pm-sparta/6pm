package com.fandom.order_service.order.presentation.controller;

import com.fandom.common.dto.ApiResponse;
import com.fandom.order_service.order.application.query.OrderQueryService;
import com.fandom.order_service.order.presentation.dto.response.OrderSummaryResponse;
import com.fandom.order_service.order.presentation.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자용 주문 관리 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/v1")
public class AdminOrderController {

    private final OrderQueryService orderQueryService;

    /**
     * 수동 검토 필요 주문 목록. 환불 복구 배치가 재시도를 소진한 주문 조회.
     * 최신 상태 변경 순으로 정렬된다.
     */
    @GetMapping("/orders/manual-review")
    public ApiResponse<PageResponse<OrderSummaryResponse>> getManualReviewOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ApiResponse.success(orderQueryService.getManualReviewOrders(page, size));
    }
}
