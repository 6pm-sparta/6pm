package com.fandom.order_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 order.* 값을 타입 세이프하게 바인딩한다.
 *
 * hold.claimTtlSeconds: holdId 1차 클레임(SETNX) TTL. "DB INSERT가 끝날 때까지 버티는" 안전판 정도의 짧은 값이면 된다.
 * hold.cacheTtlSeconds: INSERT 성공 후 {holdId: orderId} 최종 캐시 TTL.
 *   API 명세서 미확정 항목: Ticketing 좌석 선점 TTL과 동일하게 맞춰야 함.
 *   확인 전까지는 기본값(application.yml)을 임시로 사용한다.
 * expirationMinutes: 주문 생성 시 expired_at = 생성시각 + expirationMinutes.
 * payment.lockWaitSeconds/lockHoldSeconds: 결제 요청 분산락(Redisson RLock) 대기/점유 시간.
 *   점유 시간은 "PENDING→PAYMENT_REQUESTED 전이 + 커밋"만 감싸는 짧은 구간 기준이며 PG 호출 전체를 포함하지 않는다.
 * payment.idempotencyKeyTtlSeconds: Idempotency-Key 멱등성 캐시 TTL. 주문 expirationMinutes와 맞춤.
 */
@ConfigurationProperties(prefix = "order")
public record OrderProperties(
        Hold hold,
        int expirationMinutes,
        PaymentLockProperties paymentLockProperties,
        Cancellation cancellation) {
    public record Hold(
            long claimTtlSeconds,
            long cacheTtlSeconds
    ) {
    }

    public record PaymentLockProperties(
            long lockWaitSeconds,
            long lockHoldSeconds,
            long idempotencyKeyTtlSeconds
    ) {
    }

    /**
     * cancellableWindowHours: CONFIRMED 상태 주문의 취소 가능 시간(시간 단위). 확정 시각
     * (statusUpdatedAt) 기준으로 계산한다.
     * 일단 임의값(24h)으로 설정.
     */
    public record Cancellation(
            long cancellableWindowHours
    ) {
    }
}
