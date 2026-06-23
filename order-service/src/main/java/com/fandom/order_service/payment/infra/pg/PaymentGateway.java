package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;

import java.util.UUID;

/**
 * PG 연동 인터페이스(DIP).
 *
 * 현재는 MockPaymentGateway 구현체만 존재한다. 추후 실제 PG사 연동이 필요해지면
 * 이 인터페이스를 구현하는 새 클래스를 추가하고 빈 설정만 교체하면 되며,
 * 호출하는 서비스 로직(PaymentRequestService 등)은 변경되지 않는 것을 목표로 한다.
 *
 * requestApproval/requestRefund(동기)는 비동기 전환 완료 시 삭제 예정.
 */
public interface PaymentGateway {

    /**
     * 결제 승인을 요청한다. amount는 반드시 DB(orders.total_amount) 기준값을 전달해야 한다
     * (클라이언트가 보낸 금액을 그대로 신뢰하지 않는다).
     */
    PgApprovalResult requestApproval(String idempotencyKey, Long amount, PaymentMethod paymentMethod);

    /**
     * 환불을 요청한다. pgTransactionId 기준으로 PG 측에서 멱등 처리되는 것을 가정한다.
     */
    PgRefundResult requestRefund(String pgTransactionId, Long amount);

    /**
     * 비동기 결제 승인을 요청한다. PG가 요청을 접수했다는 사실과 거래 식별자(pgTransactionId)만
     * 즉시 반환하며, 실제 승인/거절 결과는 알 수 없다 — 최종 결과는 추후 PG 콜백(webhook)으로 전달된다.
     */
    String requestApprovalAsync(UUID orderId, String idempotencyKey, Long amount, PaymentMethod paymentMethod);

    /**
     * 비동기 환불을 요청한다. 최종 결과(REFUNDED/FAILED)는 webhook 콜백으로 전달된다.
     */
    void requestRefundAsync(UUID orderId, String pgTransactionId, Long amount);
}