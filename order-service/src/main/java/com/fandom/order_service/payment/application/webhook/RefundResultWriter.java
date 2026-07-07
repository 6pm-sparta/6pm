package com.fandom.order_service.payment.application.webhook;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PG 환불 webhook(REFUNDED/REFUND_FAILED) 결과를 Order/Payment에 반영한다.
 *
 * 유저 직접 취소({@code OrderCancelWriter})와 SAGA 보상({@code OrderCompensationWriter})은 둘 다
 * PG에 환불을 요청하기 "전"에 이미 order는 CANCEL_REQUESTED로, payment는 REFUND_REQUESTED로
 * 전이해둔다.
 */
@Component
@RequiredArgsConstructor
public class RefundResultWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxAppender outboxAppender;

    /**
     * 환불 성공(REFUNDED) webhook을 반영한다.
     * 전이 시 같은 트랜잭션에서 좌석 반환/환불 알림 이벤트를 Outbox에 적재. 중복 수신은 no-op.
     */
    @Transactional
    public void applyRefundSuccess(UUID orderId, UUID paymentId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.CANCEL_REQUESTED) {
            return;
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        OrderStatus before = order.getStatus();
        order.markCancelCompleted();
        payment.refund();
        saveHistory(order.getId(), before, order.getStatus(), "[USER] 환불 완료(PG 웹훅)");

        outboxAppender.appendPaymentCancelled(order.getId());
        outboxAppender.appendOrderCancelledNotification(order.getId(), order.getUserId());
    }

    /**
     * 환불 거절(REFUND_FAILED) webhook 반영. 최종 실패로 처리하고 수동 처리 대상으로 남긴다(발행 없음).
     * order/payment FAILED로 전이.
     */
    @Transactional
    public void applyRefundFailure(UUID orderId, UUID paymentId, String failureReason) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.CANCEL_REQUESTED) {
            return;
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        OrderStatus before = order.getStatus();
        order.markCancelFailed();
        payment.refundFail(failureReason);
        saveHistory(order.getId(), before, order.getStatus(), "[USER] 환불 거절(PG 웹훅): " + failureReason);
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
