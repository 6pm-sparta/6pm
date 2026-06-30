package com.fandom.order_service.order.application.timeout;

/**
 * 단건 타임아웃 처리 결과.
 * CANCELLED: 전이 성공. SKIPPED: 이미 다른 경로로 상태가 바뀜(정상 경합, 에러 아님).
 */
public enum OrderTimeoutResult {
    CANCELLED,
    SKIPPED
}
