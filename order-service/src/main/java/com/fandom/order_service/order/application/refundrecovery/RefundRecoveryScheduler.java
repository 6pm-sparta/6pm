package com.fandom.order_service.order.application.refundrecovery;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 환불 미완료 복구 배치.
 * CANCEL_REQUESTED/FAILED 상태 주문을 폴링해 건당 RefundRecoveryWriter에 위임한다.
 * 한 건의 예외가 배치 전체를 막지 않도록 건별 try-catch한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRecoveryScheduler {

    private final OrderRepository orderRepository;
    private final RefundRecoveryWriter refundRecoveryWriter;
    private final OrderProperties orderProperties;

    @Scheduled(fixedDelayString = "${order.refund-recovery.poll-interval-ms:10000}")
    public void recoverIncompleteRefunds() {

        List<UUID> candidateIds = orderRepository.findRefundRecoveryCandidateOrderIds(
                OrderStatus.CANCEL_REQUESTED,
                OrderStatus.FAILED,
                Limit.of(orderProperties.refundRecovery().batchSize()));

        if (candidateIds.isEmpty()) {
            return;
        }

        int synced = 0;
        int retried = 0;
        int exhausted = 0;
        int skipped = 0;
        int failed = 0;

        for (UUID orderId : candidateIds) {
            try {
                RefundRecoveryResult result = refundRecoveryWriter.recover(orderId);
                switch (result) {
                    case SYNCED -> synced++;
                    case RETRIED -> retried++;
                    case EXHAUSTED -> exhausted++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("[환불 복구] 단건 처리 실패. orderId={}", orderId, e);
            }
        }

        log.info("[환불 복구] 배치 처리 완료. 대상={}, 동기화={}, 재시도={}, 수동전환={}, 스킵={}, 실패={}",
                candidateIds.size(), synced, retried, exhausted, skipped, failed);
    }
}
