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
 */
@ConfigurationProperties(prefix = "order")
public record OrderProperties(
        Hold hold,
        int expirationMinutes
) {
    public record Hold(
            long claimTtlSeconds,
            long cacheTtlSeconds
    ) {
    }
}
