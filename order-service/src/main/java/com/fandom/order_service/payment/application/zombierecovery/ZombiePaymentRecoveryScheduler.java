package com.fandom.order_service.payment.application.zombierecovery;

import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 좀비 결제 정리 배치. 만료됐지만 REQUESTED 결제가 진행중 플래그로 남아 멈춘 PENDING 주문을 폴링해
 * 건당 ZombiePaymentRecoveryWriter에 위임한다. 건별 try-catch로 한 건의 예외가 배치 전체를
 * 막지 않게 한다.
 */
@Slf4j
@Component
public class ZombiePaymentRecoveryScheduler {

    private final PaymentRepository paymentRepository;
    private final ZombiePaymentRecoveryWriter zombiePaymentRecoveryWriter;
    private final int batchSize;

    public ZombiePaymentRecoveryScheduler(
            PaymentRepository paymentRepository,
            ZombiePaymentRecoveryWriter zombiePaymentRecoveryWriter,
            @Value("${order.zombie-payment-recovery.batch-size:100}") int batchSize) {
        this.paymentRepository = paymentRepository;
        this.zombiePaymentRecoveryWriter = zombiePaymentRecoveryWriter;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${order.zombie-payment-recovery.poll-interval-ms:15000}")
    public void recoverZombiePayments() {

        List<UUID> candidateIds = paymentRepository.findZombiePaymentOrderIds(
                PaymentStatus.REQUESTED,
                OrderStatus.PENDING,
                LocalDateTime.now(),
                Limit.of(batchSize));

        if (candidateIds.isEmpty()) {
            return;
        }

        int approved = 0;
        int failed = 0;
        int skipped = 0;
        int errored = 0;

        for (UUID orderId : candidateIds) {
            try {
                ZombiePaymentRecoveryResult result = zombiePaymentRecoveryWriter.recover(orderId);
                switch (result) {
                    case APPROVED_SYNCED -> approved++;
                    case FAILED_SYNCED -> failed++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException e) {
                errored++;
                log.error("[좀비 결제 정리] 단건 처리 실패. orderId={}", orderId, e);
            }
        }

        log.info("[좀비 결제 정리] 배치 처리 완료. 대상={}, 승인동기화={}, 실패처리={}, 스킵={}, 에러={}",
                candidateIds.size(), approved, failed, skipped, errored);
    }
}
