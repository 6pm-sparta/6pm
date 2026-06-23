package com.fandom.order_service.payment.application.webhook;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.infra.pg.PgWebhookHmacUtil;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * PG 콜백(Webhook) 수신 처리. 서명 검증 + pgTransactionId 기준 중복수신 차단까지만 책임진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgWebhookService {

    private static final String DEDUPE_KEY_PREFIX = "webhook:pg:";
    private static final String CLAIM_MARKER = "RECEIVED";

    private final PgWebhookHmacUtil signatureVerifier;
    private final StringRedisTemplate redisTemplate;
    private final OrderProperties orderProperties;

    public void receive(PgWebhookRequest request, String signature) {

        // PG가 보낸 건지 확인
        if (!signatureVerifier.verify(request, signature)) {
            log.warn("[PG Webhook] 서명 검증 실패. pgTransactionId={}, orderId={}",
                    request.pgTransactionId(), request.orderId());
            throw new CustomException(PaymentErrorCode.INVALID_SIGNATURE);
        }

        // 이미 처리한 웹훅인지 확인
        if (!claim(request.pgTransactionId())) {
            log.info("[PG Webhook] 중복 수신 — 무시. pgTransactionId={}", request.pgTransactionId());
            return;
        }

        // TODO(#109, #110): status에 따른 결제 승인/실패, 환불 완료 도메인 디스패치 연결
        log.info("[PG Webhook] 수신 완료. pgTransactionId={}, orderId={}, status={}, amount={}",
                request.pgTransactionId(), request.orderId(), request.status(), request.amount());
    }

    private boolean claim(String pgTransactionId) {

        String key = DEDUPE_KEY_PREFIX + pgTransactionId;

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
