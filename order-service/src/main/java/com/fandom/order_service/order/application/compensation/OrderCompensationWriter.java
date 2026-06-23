package com.fandom.order_service.order.application.compensation;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 좌석 예매 실패(ticketing.seat.book.failed) 시 SAGA 보상 트랜잭션 처리.
 *
 * 1. startCompensation: 비관적 락 안에서 PAID/CONFIRMED → COMPENSATING 전이 + 환불 대상 결제 조회.
 * 2. applyRefundSuccess: 락 밖에서 PG 환불 재시도(Service)가 성공한 뒤 COMPENSATING → REFUND_REQUESTED
 *    → REFUNDED를 한 트랜잭션에서 처리.
 * 3. applyRefundFailure: 재시도를 모두 소진한 뒤 COMPENSATING → FAILED로 종료.
 *
 * 동시성: 유저 직접 취소(OrderCancelWriter)와 이 Writer가 거의 동시에 같은 주문을 건드릴 수 있다.
 * 락으로 이중 환불 방지.
 */
@Component
@RequiredArgsConstructor
public class OrderCompensationWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public OrderCompensationResult startCompensation(UUID orderId, String reason) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        OrderStatus before = order.getStatus();

        return switch (before) {

            case PAID, CONFIRMED -> {
                Payment payment = findApprovedPayment(order.getId());
                order.markCompensating();
                saveHistory(order.getId(), before, order.getStatus(),
                        "SAGA 보상 시작 - 좌석 예매 실패: " + reason);
                yield OrderCompensationResult.compensatingStarted(order.getId(), payment, order.getUserId());
            }

            case COMPENSATING, REFUND_REQUESTED, REFUNDED, FAILED, CANCELLED ->
                // 이미 다른 경로로 처리 중/완료된 주문은 예외를 던지지 않고 멱등 처리한다.
                    OrderCompensationResult.alreadyHandled(order.getId(), order.getStatus());

            // PENDING, PAYMENT_REQUESTED: 결제 승인 전 단계라 좌석 확정 시도 자체가 있을 수 없는 상태(방어).
            default -> OrderCompensationResult.skippedInvalidState(order.getId(), order.getStatus());
        };
    }

    @Transactional
    public OrderCompensationResult applyRefundSuccess(UUID orderId, UUID paymentId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        OrderStatus step1Before = order.getStatus();
        order.markRefundRequested();
        saveHistory(order.getId(), step1Before, order.getStatus(), "SAGA 보상 - 환불 요청");

        OrderStatus step2Before = order.getStatus();
        order.markRefunded();
        payment.refund();
        saveHistory(order.getId(), step2Before, order.getStatus(), "SAGA 보상 - 환불 완료");

        return OrderCompensationResult.refunded(order.getId(), order.getStatusUpdatedAt());
    }

    @Transactional
    public OrderCompensationResult applyRefundFailure(UUID orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        OrderStatus before = order.getStatus();
        order.markFailed();
        saveHistory(order.getId(), before, order.getStatus(),
                "SAGA 보상 - 환불 최종 실패(재시도 소진), 수동 처리 대상");

        return OrderCompensationResult.failed(order.getId(), order.getStatusUpdatedAt());
    }

    private Payment findApprovedPayment(UUID orderId) {
        return paymentRepository.findByOrderIdAndPaymentStatus(orderId, PaymentStatus.APPROVED)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    private void saveHistory(UUID orderId, OrderStatus fromStatus, OrderStatus toStatus, String reason) {
        orderStatusHistoryRepository.save(
                OrderStatusHistory.builder()
                        .orderId(orderId)
                        .fromStatus(fromStatus)
                        .toStatus(toStatus)
                        .reason(reason)
                        .build());
    }
}
