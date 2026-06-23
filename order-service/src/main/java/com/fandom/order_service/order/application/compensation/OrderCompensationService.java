package com.fandom.order_service.order.application.compensation;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.kafka.producer.OrderEventProducer;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgRefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * ticketing.seat.book.failed 수신 → SAGA 보상 트랜잭션(SeatEventConsumer가 이 서비스를 호출).
 *
 * 재시도는 Kafka 메시지 재전송이 아니라 이 메서드 안에서 도는 동기 루프다.
 * Kafka 레벨 재전송으로 구현하면 COMPENSATING 전이 같은 부수효과가 메시지마다 중복 실행되는 걸
 * 막기 더 까다로워진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCompensationService {

    private final OrderCompensationWriter orderCompensationWriter;
    private final PaymentGateway paymentGateway;
    private final OrderEventProducer orderEventProducer;
    private final OrderProperties orderProperties;

    public void compensate(UUID orderId, String reason) {

        OrderCompensationResult started = orderCompensationWriter.startCompensation(orderId, reason);

        if (started.type() == OrderCompensationResult.Type.ALREADY_HANDLED) {
            log.info("[SAGA 보상] 이미 처리된 주문 - 이벤트 무시. orderId={}, status={}",
                    started.orderId(), started.status());
            return;
        }

        if (started.type() == OrderCompensationResult.Type.SKIPPED_INVALID_STATE) {
            log.warn("[SAGA 보상] COMPENSATING으로 전이할 수 없는 상태 - 이벤트 스킵. orderId={}, status={}",
                    started.orderId(), started.status());
            return;
        }

        retryRefund(orderId, started.paymentToRefund(), started.userId());
    }

    private void retryRefund(UUID orderId, Payment payment, UUID userId) {

        int maxAttempts = orderProperties.compensation().refundMaxAttempts();
        long backoffMillis = orderProperties.compensation().refundRetryBackoffMillis();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            PgRefundResult pgResult = paymentGateway.requestRefund(payment.getPgTransactionId(), payment.getAmount());

            if (pgResult.isSuccess()) {
                OrderCompensationResult refunded =
                        orderCompensationWriter.applyRefundSuccess(orderId, payment.getId());
                orderEventProducer.publishOrderCancelledNotification(refunded.orderId(), userId);
                log.info("[SAGA 보상] 환불 완료. orderId={}, attempt={}/{}", orderId, attempt, maxAttempts);
                return;
            }

            log.warn("[SAGA 보상] 환불 재시도 {}/{} 실패. orderId={}, reason={}",
                    attempt, maxAttempts, orderId, pgResult.failureReason());

            // 인터럽트 시 재시도 없이 바로 빠져나온다.
            if (!sleep(backoffMillis)) {
                log.warn("[SAGA 보상] 재시도 중 인터럽트 발생 - 루프 중단. orderId={}, attempt={}", orderId, attempt);
                break;
            }
        }

        // 최대 재시도 초과 — 수동 처리 대상으로 FAILED 전이 후 종료.
        orderCompensationWriter.applyRefundFailure(orderId);
        log.error("[SAGA 보상] 환불 최종 실패 - 수동 처리 대상. orderId={}, paymentId={}, pgTransactionId={}",
                orderId, payment.getId(), payment.getPgTransactionId());
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
