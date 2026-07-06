package com.fandom.order_service.order.application.cancellation;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.presentation.dto.response.OrderCancelResponse;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 주문 취소. 락 획득 순서는 결제 요청과 같은 원칙을 따른다(api 명세서 "주문 취소" 고려점 1, 3):
 *
 * 1. orders 비관적 락 + 본인 확인 + 상태 분기 + (필요 시) CANCEL_REQUESTED 전이 — 짧은 트랜잭션(OrderCancelWriter.decide)
 * 2. 락 밖에서 PG에 비동기 환불 요청을 접수만 시키고 즉시 응답. 실제 환불 완료/거절은 PG 웹훅으로
 *    비동기 반영된다(RefundResultWriter). 응답 시점엔 아직 CANCEL_REQUESTED 상태다.
 * 3. 환불 완료 후 좌석 반환(order.payment.cancelled)/알림(notification.send) 발행은 webhook 처리
 *    쪽(PgWebhookService)이 담당.
 *
 * PG 접수 호출이 동기적으로 실패하면(네트워크 오류 등, webhook 자체가 오지 않을
 * 상황) PG_ERROR(502)로 응답한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final OrderCancelWriter orderCancelWriter;
    private final PaymentGateway paymentGateway;

    public OrderCancelResponse cancelOrder(UUID orderId, UUID requesterId) {

        OrderCancelDecision decision = orderCancelWriter.decide(orderId, requesterId);

        if (decision.type() != OrderCancelDecision.Type.REFUND_NEEDED) {
            // CANCELLED 또는 IDEMPOTENT — PG 호출 없이 바로 응답
            return OrderCancelResponse.withoutRefund(decision.orderId(), decision.status(), decision.updatedAt());
        }

        Payment payment = decision.paymentToRefund();

        try {
            paymentGateway.requestRefundAsync(decision.orderId(), payment.getPgTransactionId(), payment.getAmount());
        } catch (RuntimeException pgFailure) {
            log.error("[주문 취소] PG 환불 접수 실패. orderId={}, paymentId={}, pgTransactionId={}",
                    decision.orderId(), payment.getId(), payment.getPgTransactionId(), pgFailure);
            throw new CustomException(PaymentErrorCode.PG_ERROR);
        }

        return OrderCancelResponse.refundRequested(decision.orderId(), payment.getId(), decision.updatedAt());
    }
}
