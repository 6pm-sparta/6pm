package com.fandom.order_service.order.application.confirmation;

import com.fandom.order_service.kafka.producer.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * ticketing.seat.booked 수신 → 주문 CONFIRMED 전이 → notification.send(ORDER_COMPLETED) 발행.
 * SeatEventConsumer가 이 서비스를 호출한다(설계 문서 5. 해피패스 플로우, 구간3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConfirmationService {

    private final OrderConfirmationWriter orderConfirmationWriter;
    private final OrderEventProducer orderEventProducer;

    public void confirmOrder(UUID orderId) {

        OrderConfirmationResult result = orderConfirmationWriter.confirm(orderId);

        switch (result.type()) {
            case CONFIRMED -> {
                orderEventProducer.publishOrderCompletedNotification(result.orderId(), result.userId());
                log.info("[좌석 확정] 주문 CONFIRMED 전이 완료. orderId={}", result.orderId());
            }
            case ALREADY_CONFIRMED ->
                    log.info("[좌석 확정] 이미 CONFIRMED 처리된 주문 - 중복 이벤트 무시. orderId={}", result.orderId());
            case SKIPPED_INVALID_STATE ->
                    log.warn("[좌석 확정] CONFIRMED로 전이할 수 없는 상태 - 이벤트 스킵. orderId={}", result.orderId());
        }
    }
}
