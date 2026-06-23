package com.fandom.order_service.payment.application.request;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.common.exception.CustomException;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 결제 요청 처리 중 두 군데에서 별도 트랜잭션이 필요해 PaymentRequestService와 물리적으로 분리한다
 * (OrderCreationWriter와 동일한 이유 — self-invocation은 Spring AOP 프록시를 거치지 않아
 * @Transactional 경계가 생기지 않으므로 다른 빈으로 분리해야 한다).
 *
 * 1. markPaymentRequestedAndSave: 분산락(Redisson RLock) "안에서" 짧게 커밋되어야 하는 구간.
 *    PENDING → PAYMENT_REQUESTED 전이 + 결제 시도 레코드(Payment, REQUESTED) INSERT.
 *    이 트랜잭션이 커밋된 뒤에야 분산락을 해제하고 PG를 호출한다.
 * 2. applyApproval/applyFailure: PG 호출(락 밖, 동기) 완료 후 결과를 반영하는 별도 트랜잭션.
 *    PG 호출 자체는 트랜잭션을 물고 있으면 안 되므로(외부 API 응답 대기 동안 DB 커넥션을 쥐고 있게 됨),
 *    호출이 끝난 뒤 새 트랜잭션에서 짧게 결과만 반영한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentRequestWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment markPaymentRequestedAndSave(UUID orderId, UUID requesterId, PaymentMethod paymentMethod,
                                               String idempotencyKey) {

        // 비관적 락(SELECT FOR UPDATE) 조회 + 상태 검증. 분산락 안에서 호출되므로 인스턴스 간 동시
        // 요청은 이미 막혀 있지만, 분산락이 뚫렸을 때(장애 등)의 최종 방어선으로 비관적 락을 유지한다.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        // 본인 주문만 결제 요청 가능 — api 명세서 결제 요청 Authorization 헤더 요건.
        // 비관적 락으로 조회한 이 시점에서 검증해야 락 안에서 일관되게 처리된다.
        if (!order.getUserId().equals(requesterId)) {
            throw new CustomException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CustomException(PaymentErrorCode.INVALID_ORDER_STATUS);
        }

        OrderStatus before = order.getStatus();
        order.markPaymentRequested();
        saveHistory(order.getId(), before, order.getStatus(), "결제 요청");

        // 결제 금액은 클라이언트 요청이 아니라 항상 DB(orders.total_amount) 기준이다
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .amount(order.getTotalAmount())
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(paymentMethod)
                .idempotencyKey(idempotencyKey)
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional
    public void applyApproval(UUID orderId, UUID paymentId, String pgTransactionId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        OrderStatus before = order.getStatus();
        order.markPaid();
        payment.approve(pgTransactionId);
        saveHistory(order.getId(), before, order.getStatus(), "결제 승인");
    }

    @Transactional
    public void applyFailure(UUID orderId, UUID paymentId, String failureReason) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        OrderStatus before = order.getStatus();
        order.markFailed();
        payment.fail(failureReason);
        saveHistory(order.getId(), before, order.getStatus(), "결제 실패: " + failureReason);
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