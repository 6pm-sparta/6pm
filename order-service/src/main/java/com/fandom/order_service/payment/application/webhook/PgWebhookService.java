package com.fandom.order_service.payment.application.webhook;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.application.request.PaymentRequestWriter;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PgWebhookHmacUtil;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * PG 콜백(Webhook) 수신 처리. 서명 검증 + pgTransactionId 기준 중복수신 차단 + 상태별 도메인 디스패치.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgWebhookService {

    private static final String DEDUPE_KEY_PREFIX = "webhook:pg:";
    private static final String CLAIM_MARKER = "RECEIVED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_REFUNDED = "REFUNDED";
    private static final String STATUS_REFUND_FAILED = "REFUND_FAILED";

    private final PgWebhookHmacUtil signatureVerifier;
    private final StringRedisTemplate redisTemplate;
    private final OrderProperties orderProperties;
    private final PaymentRepository paymentRepository;
    private final PaymentRequestWriter paymentRequestWriter;
    private final RefundResultWriter refundResultWriter;

    public void receive(PgWebhookRequest request, String signature) {

        // PG가 보낸 건지 확인
        if (!signatureVerifier.verify(request, signature)) {
            log.warn("[PG Webhook] 서명 검증 실패. pgTransactionId={}, orderId={}",
                    request.pgTransactionId(), request.orderId());
            throw new CustomException(PaymentErrorCode.INVALID_SIGNATURE);
        }

        // 이미 처리한 웹훅인지 확인
        if (!claim(request.pgTransactionId(), request.status())) {
            log.info("[PG Webhook] 중복 수신 — 무시. pgTransactionId={}", request.pgTransactionId());
            return;
        }

        dispatch(request);
    }

    /**
     * Webhook 상태에 따라 결제 승인/실패 처리를 수행한다.
     * 이미 처리된 요청은 no-op 처리되어 이벤트를 중복 발행하지 않는다.
     */
    private void dispatch(PgWebhookRequest request) {

        Optional<Payment> paymentOpt = paymentRepository.findByPgTransactionId(request.pgTransactionId());
        if (paymentOpt.isEmpty()) {

            // 알 수 없는 pgTransactionId, 재전송 루프를 막기 위해 로그만 남기고 종료한다.
            log.error("[PG Webhook] pgTransactionId에 해당하는 결제 건을 찾을 수 없습니다. pgTransactionId={}, orderId={}",
                    request.pgTransactionId(), request.orderId());
            return;
        }

        Payment payment = paymentOpt.get();

        if (!payment.getAmount().equals(request.amount())) {

            // 결제 금액이 일치하지 않으면 적용하지 않는다.
            log.error("[PG Webhook] amount 불일치로 적용하지 않습니다. paymentId={}, expected={}, received={}",
                    payment.getId(), payment.getAmount(), request.amount());
            return;
        }

        // 상태 전이와 Outbox 적재는 Writer 트랜잭션 안에서 함께 처리된다(중복 수신은 Writer가 no-op).
        switch (request.status()) {
            case STATUS_APPROVED -> paymentRequestWriter.applyApproval(request.orderId(), payment.getId());
            case STATUS_FAILED -> handleFailed(request, payment);
            case STATUS_REFUNDED -> refundResultWriter.applyRefundSuccess(request.orderId(), payment.getId());
            case STATUS_REFUND_FAILED -> {
                String reason = request.failureReason() != null ? request.failureReason() : "PG 환불 거절";
                // FAILED 전이 + 로그로 남기고 수동 처리 대상으로 남김.
                refundResultWriter.applyRefundFailure(request.orderId(), reason);
            }
            default -> log.warn("[PG Webhook] 알 수 없는 status — 무시. status={}, pgTransactionId={}",
                    request.status(), request.pgTransactionId());
        }
    }

    /** TRANSIENT: prefix → 재시도 마킹(Order 상태 유지). 그 외 → 영구 실패. */
    private void handleFailed(PgWebhookRequest request, Payment payment) {

        String reason = request.failureReason() != null ? request.failureReason() : "PG 응답 실패";

        if (isTransient(reason)) {
            paymentRequestWriter.applyFailureWithRetry(request.orderId(), payment.getId(), reason);
            log.info("[PG Webhook] 일시적 오류 — 재시도 마킹. orderId={}, paymentId={}, reason={}",
                    request.orderId(), payment.getId(), reason);
        } else {
            paymentRequestWriter.applyFailure(request.orderId(), payment.getId(), reason);
        }
    }

    private static boolean isTransient(String failureReason) {
        return failureReason != null && failureReason.startsWith("TRANSIENT:");
    }

    private boolean claim(String pgTransactionId, String status) {

        String key = DEDUPE_KEY_PREFIX + pgTransactionId + ":" + status;

        try {
            // SET key "RECEIVED" NX EX {dedupeTtlSeconds} — 같은 pgTransactionId로 먼저 온 콜백이
            // 없으면 true(내가 선점), 이미 있으면 false(중복 수신)
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                    key, CLAIM_MARKER, Duration.ofSeconds(orderProperties.pgWebhook().dedupeTtlSeconds()));
            return Boolean.TRUE.equals(claimed);

        } catch (DataAccessException redisDown) {
            log.warn("[PG Webhook] Redis 장애로 중복수신 1차 방어를 건너뜁니다. pgTransactionId={}",
                    pgTransactionId, redisDown);
            return true;
        }
    }
}
