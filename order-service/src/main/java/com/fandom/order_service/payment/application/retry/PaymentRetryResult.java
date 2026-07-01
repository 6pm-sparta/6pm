package com.fandom.order_service.payment.application.retry;

import com.fandom.order_service.payment.domain.entity.Payment;

/**
 * 단건 결제 재시도 처리 결과.
 *
 * RETRYING: 새 Payment 생성 완료, PG 재요청 진행 중.
 * EXHAUSTED: 재시도 횟수 초과 → Order FAILED 전이.
 * SKIPPED: 처리 시점에 이미 상태 변경됨(정상 경합).
 */
public record PaymentRetryResult(
        Type type,
        Payment retryPayment
) {

    public enum Type {
        RETRYING,
        EXHAUSTED,
        SKIPPED
    }

    public static PaymentRetryResult retrying(Payment payment) {
        return new PaymentRetryResult(Type.RETRYING, payment);
    }

    public static final PaymentRetryResult EXHAUSTED = new PaymentRetryResult(Type.EXHAUSTED, null);
    public static final PaymentRetryResult SKIPPED = new PaymentRetryResult(Type.SKIPPED, null);
}
