package com.fandom.order_service.payment.application.webhook;

import com.fandom.common.exception.CustomException;
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

import java.util.Optional;
import java.util.UUID;

/**
 * PG 환불 webhook(REFUNDED/REFUND_FAILED) 결과를 Order/Payment에 반영한다.
 *
 * 유저 직접 취소({@code OrderCancelWriter})와 SAGA 보상({@code OrderCompensationWriter})은 둘 다
 * PG에 환불을 요청하기 "전"에 이미 REFUND_REQUESTED로 전이해둔다.
 */
@Component
@RequiredArgsConstructor
public class RefundResultWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 환불 성공(REFUNDED) webhook을 반영한다.
     *
     * @return 실제 전이가 발생했다면 알림 발송에 필요한 userId, 이미 처리된 중복 수신이면 empty
     */
    @Transactional
    public Optional<UUID> applyRefundSuccess(UUID orderId, UUID paymentId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            return Optional.empty();
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        OrderStatus before = order.getStatus();
        order.markRefunded();
        payment.refund();
        saveHistory(order.getId(), before, order.getStatus(), "환불 완료(PG 웹훅)");

        return Optional.of(order.getUserId());
    }

    /**
     * 환불 거절(REFUND_FAILED) webhook을 반영한다.
     * 유저 직접 취소/SAGA 보상 경로 모두 동일하게 최종 실패로 처리한다.
     *
     * @return 실제 전이가 발생했는지 여부
     */
    @Transactional
    public boolean applyRefundFailure(UUID orderId, String failureReason) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            return false;
        }

        OrderStatus before = order.getStatus();
        order.markRefundFailed();
        saveHistory(order.getId(), before, order.getStatus(), "환불 거절(PG 웹훅): " + failureReason);

        return true;
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
