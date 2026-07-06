package com.fandom.ticketing_service.order.infrastructure.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * order-service 장애/지연이 releaseHold() 자체를 막지 않도록 감싼다.
 * 여기서 최종 실패해도 order-service의 타임아웃 자동취소(#231)가 order.hold.released를 발행하고
 * PaymentEventConsumer.onHoldReleased() → SeatConfirmService.releaseSeat()가 멱등하게 뒤따라오므로,
 * 재시도/서킷브레이커로 최선을 다한 뒤 실패하면 로그만 남기고 삼킨다(예외를 releaseHold()로 전파하지 않음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderClientRetryWrapper {

    private final OrderClient orderClient;

    // TODO: order-service에 DELETE /internal/v1/orders/{orderId} 엔드포인트가 아직 없어서(order 파트
    // 승인 대기 중) 이 호출은 현재 항상 404로 실패하고 아래 폴백으로 흡수된다. 엔드포인트 추가되면 정상 동작.
    @Retry(name = "orderClient", fallbackMethod = "cancelFallback")
    @CircuitBreaker(name = "orderClient")
    public void cancel(UUID orderId, UUID requesterId) {
        orderClient.cancel(orderId, requesterId);
    }

    private void cancelFallback(UUID orderId, UUID requesterId, Exception e) {
        log.error("[OrderClient] 주문 취소 실패 - 좌석 해제는 그대로 진행. orderId={}, requesterId={}, 원인={}",
                orderId, requesterId, e.getMessage());
    }
}
