package com.fandom.order_service.order.application.query;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.presentation.dto.response.OrderResponse;
import com.fandom.order_service.order.presentation.dto.response.OrderSummaryResponse;
import com.fandom.order_service.order.presentation.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 주문 조회. 단건/목록 모두 호출자의 userId와 주문 소유자가 일치하는지 검증한다 (본인 주문만 조회 가능).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderResponse getOrder(UUID orderId, UUID requesterId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        validateOwner(order, requesterId);

        return OrderResponse.from(order);
    }

    public PageResponse<OrderSummaryResponse> getOrders(UUID requesterId, int page, int size) {

        validatePagingParams(page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = orderRepository.findByUserId(requesterId, pageable);

        return PageResponse.from(orders.map(OrderSummaryResponse::from));
    }

    private void validatePagingParams(int page, int size) {

        if (page < 0 || size < 1) {
            throw new CustomException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateOwner(Order order, UUID requesterId) {
        if (!order.getUserId().equals(requesterId)) {
            throw new CustomException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }
}
