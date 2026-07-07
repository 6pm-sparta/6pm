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
 * 좌석 예매 실패(ticketing.seat.book.failed) 시 SAGA 보상 트랜잭션의 1단계 처리.
 *
 * startCompensation: 비관적 락 안에서 CONFIRMING/CONFIRMED → CANCEL_REQUESTED로 전이하고,
 * 환불 대상 결제를 함께 반환한다.
 * (이전엔 COMPENSATING을 거쳐 REFUND_REQUESTED로 갔지만, COMPENSATING은 애초에
 * orders 테이블에 persist되지 않는 transient 상태였다 — 같은 트랜잭션 안에서 바로 다음 상태로
 * 넘어가 history 테이블에만 두 행으로 남았다. 이번에 한 단계로 합치고, "SAGA 경로였다"는 사실은
 * reason의 [SAGA] 프리픽스로 남긴다.)
 *
 * 동시성: 유저 직접 취소(OrderCancelWriter)와 이 Writer가 거의 동시에 같은 주문을 건드릴 수 있다.
 * 비관적 락 + 상태 검증으로 이중 처리를 방지한다.
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

            case CONFIRMING, CONFIRMED -> {
                Payment payment = findApprovedPayment(order.getId());
                payment.requestRefund();

                order.markCancelRequested();
                saveHistory(order.getId(), before, order.getStatus(),
                        "[SAGA] 좌석 예매 실패로 보상 시작 및 환불 요청: " + reason);

                yield OrderCompensationResult.refundRequestedStarted(order.getId(), payment, order.getUserId());
            }

            case CANCEL_REQUESTED, CANCELLED, FAILED ->
                // 이미 다른 경로로 처리 중/완료된 주문은 예외를 던지지 않고 멱등 처리한다.
                    OrderCompensationResult.alreadyHandled(order.getId(), order.getStatus());

            // PENDING: 결제 승인 전 단계라 좌석 확정 시도 자체가 있을 수 없는 상태(방어).
            default -> OrderCompensationResult.skippedInvalidState(order.getId(), order.getStatus());
        };
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
