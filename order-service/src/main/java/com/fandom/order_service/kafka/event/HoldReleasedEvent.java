package com.fandom.order_service.kafka.event;

import java.util.UUID;

/**
 * order.hold.released 발행 payload. 결제 전(PENDING) 좌석 선점 해제 전용 — 유저 직접 취소(결제 전)와
 * 타임아웃 자동 취소 둘 다 이 이벤트를 발행한다.
 */
public record HoldReleasedEvent(UUID orderId) {
}
