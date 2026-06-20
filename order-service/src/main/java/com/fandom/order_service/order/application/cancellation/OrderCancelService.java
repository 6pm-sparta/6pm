package com.fandom.order_service.order.application.cancellation;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.presentation.dto.response.OrderCancelResponse;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgRefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 주문 취소. 락 획득 순서는 결제 요청과 같은 원칙을 따른다(api 명세서 "주문 취소" 고려점 1, 3):
 *
 * 1. orders 비관적 락 + 본인 확인 + 상태 분기 + (필요 시) REFUND_REQUESTED 전이 — 짧은 트랜잭션(OrderCancelWriter.decide)
 * 2. 락 밖에서 PG 환불 호출 (PAID/CONFIRMED인 경우만, 동기, MVP)
 * 3. 환불 결과 반영 — 별도 트랜잭션(OrderCancelWriter.applyRefundSuccess)
 *
 * PG 호출을 락 밖에 두는 이유는 결제 요청과 동일하다 — 외부 API 호출 동안 DB 트랜잭션/커넥션을
 * 쥐고 있지 않기 위함. PG 호출이 진행되는 동안 들어오는 동시 취소 요청은 1번 단계에서 주문 상태가
 * 이미 REFUND_REQUESTED(PAID/CONFIRMED 아님)인 것을 보고 즉시 거부된다.
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
        PgRefundResult pgResult = paymentGateway.requestRefund(payment.getPgTransactionId(), payment.getAmount());

        if (!pgResult.isSuccess()) {
            // MVP 리스크(api 명세서 미확정 항목): 환불 실패 시 주문은 REFUND_REQUESTED에 머문다.
            // 복구 배치(스케줄러 + PG 거래 조회)는 별도 이슈로 분리, 현재는 수동 처리 대상으로 로그만 남김.
            log.error("[주문 취소] PG 환불 실패. orderId={}, paymentId={}, pgTransactionId={}, reason={}",
                    decision.orderId(), payment.getId(), payment.getPgTransactionId(), pgResult.failureReason());
            throw new CustomException(PaymentErrorCode.PG_ERROR);
        }

        OrderCancelDecision refunded = orderCancelWriter.applyRefundSuccess(decision.orderId(), payment.getId());

        return OrderCancelResponse.refunded(
                refunded.orderId(), refunded.status(), refunded.refundedPaymentId(), refunded.updatedAt());
    }
}
