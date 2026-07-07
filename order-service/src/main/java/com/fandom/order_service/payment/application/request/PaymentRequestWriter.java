package com.fandom.order_service.payment.application.request;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.common.exception.CustomException;
import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 결제 요청 처리 중 세 군데에서 별도 트랜잭션이 필요해 PaymentRequestService와 물리적으로 분리한다
 * (OrderCreationWriter와 동일한 이유 — self-invocation은 Spring AOP 프록시를 거치지 않아
 * @Transactional 경계가 생기지 않으므로 다른 빈으로 분리해야 한다).
 *
 * 1. markPaymentRequestedAndSave: 분산락(Redisson RLock) "안에서" 짧게 커밋되어야 하는 구간.
 *    orders.status는 PENDING 그대로 두고, 결제 시도 레코드(Payment, REQUESTED)만 INSERT한다.
 *    동시 결제 요청 차단은 이제 "이 주문에 이미 REQUESTED Payment가 있는가"로 판단한다.
 * 2. recordPgTransactionId: PG가 요청 접수를 ack하며 즉시 반환한 pgTransactionId를 기록하는
 *    별도 트랜잭션. 승인/거절 여부는 아직 모른다 — webhook이 그 결과를 들고 온다.
 * 3. applyApproval/applyFailure: PG webhook 콜백을 받았을 때 결과를 반영하는 트랜잭션.
 *    PgWebhookService가 pgTransactionId로 Payment를 찾아 호출한다. 멱등성/유효성 판단은
 *    1차로 Payment.paymentStatus == REQUESTED인지(이 webhook이 아직 처리 안 된 결과인지),
 *    2차로 order.status == PENDING인지(다른 경로로 이미 종결되지 않았는지) 두 단계로 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequestWriter {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxAppender outboxAppender;

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

        // PENDING이 "결제 시도 전"과 "결제 시도 중"을 모두 포괄하게 되면서, 진행중 결제 존재 여부를
        // payments 테이블에서 직접 확인해야 동시 결제 요청(중복 PG 호출)을 막을 수 있다.
        // orders row 락을 이미 쥔 상태이므로 이 체크~INSERT 사이에는 경합이 없다.
        if (paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED)) {
            throw new CustomException(PaymentErrorCode.INVALID_ORDER_STATUS);
        }

        // 결제 금액은 클라이언트 요청이 아니라 항상 DB(orders.total_amount) 기준이다
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .amount(order.getTotalAmount())
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(paymentMethod)
                .idempotencyKey(idempotencyKey)
                .build();

        Payment saved = paymentRepository.save(payment);
        order.updateLatestPayment(saved.getId());

        // orders.status는 변경 없음(PENDING 유지)이지만, 결제 시도 자체는 감사 기록에 남긴다.
        saveHistory(order.getId(), OrderStatus.PENDING, OrderStatus.PENDING, "[USER] 결제 요청");

        return saved;
    }

    /**
     * PG 요청 접수 시 반환된 pgTransactionId를 저장한다.
     * 실제 승인/거절 결과는 Webhook으로 반영된다.
     */
    @Transactional
    public void recordPgTransactionId(UUID paymentId, String pgTransactionId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        payment.recordPgTransactionId(pgTransactionId);
    }

    /** 승인 Webhook 반영. 전이 시 같은 트랜잭션에서 결제완료 이벤트를 Outbox에 적재. 중복 수신은 no-op. */
    @Transactional
    public void applyApproval(UUID orderId, UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // 1차: 이 webhook이 아직 처리 안 된 결과인지 Payment 기준으로 판단(중복 수신 no-op).
        if (payment.getPaymentStatus() != PaymentStatus.REQUESTED) {
            return;
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        // 2차: 다른 경로(유저 취소, 타임아웃)로 이미 종결된 주문이면 승인 웹훅을 반영하지 않는다.
        // 이 케이스는 데이터 불일치 신호이므로 로그를 남긴다 — 정상 흐름이라면 발생하지 않아야 한다.
        if (order.getStatus() != OrderStatus.PENDING) {
            log.error("[결제 승인] 주문이 이미 다른 경로로 종결됨. orderId={}, orderStatus={}, paymentId={}",
                    orderId, order.getStatus(), paymentId);
            return;
        }

        OrderStatus before = order.getStatus();
        order.markConfirming();
        payment.approve();
        paymentRepository.clearRetryableFlagByOrderId(orderId); // 재시도 폴링 대상에서 제외
        saveHistory(order.getId(), before, order.getStatus(), "[USER] 결제 승인");
        outboxAppender.appendPaymentCompleted(order.getId());
    }

    /** 실패 Webhook 반영. 전이 시 같은 트랜잭션에서 결제실패 이벤트를 Outbox에 적재. 중복 수신은 no-op. */
    @Transactional
    public void applyFailure(UUID orderId, UUID paymentId, String failureReason) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getPaymentStatus() != PaymentStatus.REQUESTED) {
            return;
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.error("[결제 실패] 주문이 이미 다른 경로로 종결됨. orderId={}, orderStatus={}, paymentId={}",
                    orderId, order.getStatus(), paymentId);
            return;
        }

        OrderStatus before = order.getStatus();
        order.markFailed();
        payment.fail(failureReason);
        saveHistory(order.getId(), before, order.getStatus(), "[USER] 결제 실패: " + failureReason);
        outboxAppender.appendPaymentFailed(order.getId());
    }

    /** 일시적 오류 webhook 반영. Order 상태 유지, Payment FAILED + retryable=true 마킹. */
    @Transactional
    public void applyFailureWithRetry(UUID orderId, UUID paymentId, String failureReason) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getPaymentStatus() != PaymentStatus.REQUESTED) {
            return;
        }

        payment.failWithRetry(failureReason);
        saveHistory(orderId, OrderStatus.PENDING, OrderStatus.PENDING,
                "[RETRY] 결제 일시적 오류 — 재시도 예정: " + failureReason);
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
