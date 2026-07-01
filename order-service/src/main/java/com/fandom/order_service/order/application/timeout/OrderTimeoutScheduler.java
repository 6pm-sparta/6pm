package com.fandom.order_service.order.application.timeout;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문 타임아웃 자동 취소 스케줄러. expired_at이 지난 PENDING 주문을 폴링해 건당
 * OrderTimeoutWriter에 위임한다. OutboxPublisher와 동일한 폴링+배치 상한 구조를 따른다.
 * 한 건의 예외가 배치 전체를 막지 않도록 건별 try-catch한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderTimeoutWriter orderTimeoutWriter;
    private final OrderProperties orderProperties;

    @Scheduled(fixedDelayString = "${order.timeout.poll-interval-ms:5000}")
    public void expireTimedOutOrders() {

        List<UUID> candidateIds = orderRepository.findExpiredOrderIds(
                OrderStatus.PENDING,
                LocalDateTime.now(),
                Limit.of(orderProperties.timeout().batchSize()));

        if (candidateIds.isEmpty()) {
            return;
        }

        int cancelled = 0;
        int skipped = 0;
        int failed = 0;

        for (UUID orderId : candidateIds) {
            try {
                OrderTimeoutResult result = orderTimeoutWriter.expireIfStillPending(orderId);
                if (result == OrderTimeoutResult.CANCELLED) {
                    cancelled++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("[주문 타임아웃] 단건 처리 실패. orderId={}", orderId, e);
            }
        }

        log.info("[주문 타임아웃] 배치 처리 완료. 대상={}, 취소={}, 스킵={}, 실패={}",
                candidateIds.size(), cancelled, skipped, failed);
    }
}
