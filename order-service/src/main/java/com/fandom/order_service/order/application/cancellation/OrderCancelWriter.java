package com.fandom.order_service.order.application.cancellation;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
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
 * 주문 취소 처리.
 * (self-invocation은 Spring AOP 프록시를 거치지 않아 @Transactional 경계가 생기지 않으므로
 * 다른 빈으로 분리해야 한다).
 *
 * decide: 비관적 락 안에서 상태를 판단하고, 가능하면 즉시 전이까지 끝내는 짧은 트랜잭션.
 * - PENDING(결제 시도 없음) → CANCELLED까지 여기서 끝남(PG 호출 없음)
 * - PENDING(결제 시도 진행중) → 409. — PAYMENT_REQUESTED가 PENDING에 흡수되면서
 *   생긴 케이스. PG 승인/거절 webhook이 오는 중에 취소를 허용하면 "취소됐는데 결제는 승인됨" 같은
 *   레이스가 생긴다. payments에 REQUESTED row가 있으면 웹훅 결과를 먼저 기다리게 한다.
 * - CONFIRMING/CONFIRMED(취소 가능 시간 내) → CANCEL_REQUESTED까지만 전이. 실제 PG 환불은 락 밖
 *   (Service)에서 비동기로 요청하며, 환불 완료/거절 결과는 PG 웹훅으로 비동기 반영된다
 *   (RefundResultWriter 참고). 이 클래스는 CANCEL_REQUESTED 전이까지만 책임진다.
 * - CANCELLED → 변경 없음, 멱등 응답용 현재 상태만 반환
 */
@Component
@RequiredArgsConstructor
public class OrderCancelWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final OrderProperties orderProperties;
    private final OutboxAppender outboxAppender;

    @Transactional
    public OrderCancelDecision decide(UUID orderId, UUID requesterId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        if (!order.getUserId().equals(requesterId)) {
            throw new CustomException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }

        OrderStatus before = order.getStatus();

        return switch (before) {

            case PENDING -> {
                // 결제 시도가 진행중(REQUESTED)이면 웹훅 결과를 기다린다.
                // 여기서 그냥 취소해버리면 직후 승인 webhook이 도착했을 때 "취소된 주문인데
                // 결제는 승인됨"이라는 정합성 버그가 생긴다.
                if (paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED)) {
                    throw new CustomException(OrderErrorCode.INVALID_ORDER_STATUS);
                }

                order.markCancelled();
                saveHistory(order.getId(), before, order.getStatus(), "[USER] 유저 직접 취소(결제 전)");
                outboxAppender.appendHoldReleased(order.getId());
                yield OrderCancelDecision.cancelled(order.getId(), order.getStatus(), order.getStatusUpdatedAt());
            }

            case CANCELLED ->
                // 멱등 응답: 이미 종료 지점에 도달한 주문에 대한 재요청. 변경 없이 현재 상태 그대로 반환.
                    OrderCancelDecision.idempotent(order.getId(), order.getStatus(), order.getStatusUpdatedAt());

            case CONFIRMING -> {
                Payment payment = findApprovedPayment(order.getId());
                payment.requestRefund();
                order.markCancelRequested();
                saveHistory(order.getId(), before, order.getStatus(), "[USER] 유저 직접 취소(결제 후)");
                yield OrderCancelDecision.refundNeeded(order.getId(), payment, order.getStatusUpdatedAt());
            }

            case CONFIRMED -> {
                if (!order.isWithinCancellationWindow(orderProperties.cancellation().cancellableWindowHours())) {
                    throw new CustomException(OrderErrorCode.CANCELLATION_WINDOW_EXPIRED);
                }
                Payment payment = findApprovedPayment(order.getId());
                payment.requestRefund();
                order.markCancelRequested();
                saveHistory(order.getId(), before, order.getStatus(), "[USER] 유저 직접 취소(확정 후, 취소 가능 시간 내)");
                yield OrderCancelDecision.refundNeeded(order.getId(), payment, order.getStatusUpdatedAt());
            }

            // CANCEL_REQUESTED, FAILED, MANUAL_REVIEW_REQUIRED
            default -> throw new CustomException(OrderErrorCode.INVALID_ORDER_STATUS);
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
