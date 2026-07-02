package com.fandom.order_service.payment.application.retry;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 결제 재시도 배치.
 * retryable=true인 FAILED Payment를 가진 PAYMENT_REQUESTED 주문 폴링 → 건당 Writer 위임.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRetryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentRetryWriter paymentRetryWriter;
    private final OrderProperties orderProperties;

    @Scheduled(fixedDelayString = "${order.payment-retry.poll-interval-ms:15000}")
    public void retryFailedPayments() {

        List<UUID> candidateIds = paymentRepository.findRetryableOrderIds(
                PaymentStatus.FAILED, Limit.of(orderProperties.paymentRetry().batchSize()));

        if (candidateIds.isEmpty()) {
            return;
        }

        int retrying = 0;
        int exhausted = 0;
        int skipped = 0;
        int failed = 0;

        for (UUID orderId : candidateIds) {
            try {
                PaymentRetryResult result = paymentRetryWriter.prepareRetry(orderId);
                switch (result.type()) {
                    case RETRYING -> {
                        retrying++;
                        paymentRetryWriter.requestApproval(orderId, result.retryPayment());
                    }
                    case EXHAUSTED -> exhausted++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("[결제 재시도] 단건 처리 실패. orderId={}", orderId, e);
            }
        }

        log.info("[결제 재시도] 배치 완료. 대상={}, 재시도={}, 횟수초과={}, 스킵={}, 실패={}",
                candidateIds.size(), retrying, exhausted, skipped, failed);
    }
}
