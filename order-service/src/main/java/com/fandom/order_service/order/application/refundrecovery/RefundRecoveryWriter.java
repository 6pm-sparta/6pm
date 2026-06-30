package com.fandom.order_service.order.application.refundrecovery;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 환불 미완료 복구 — 건당 트랜잭션.
 * 지금은 스케줄러가 컴파일/동작할 수 있도록 골격만 둔다.
 */
@Component
@RequiredArgsConstructor
public class RefundRecoveryWriter {

    private final OrderRepository orderRepository;

    @Transactional
    public RefundRecoveryResult recover(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null) {
            return RefundRecoveryResult.SKIPPED;
        }

        return RefundRecoveryResult.SKIPPED;
    }
}
