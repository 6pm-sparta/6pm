package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock PG 클라이언트. 실제 PG사 없이 로컬/테스트 환경에서 결제 흐름을 검증하기 위한 구현체.
 *
 * 시나리오는 임의(랜덤)가 아니라 idempotencyKey 접두사로 결정론적으로 트리거한다.
 * 랜덤 실패를 쓰면 테스트가 불안정(flaky)해지고, 특정 실패/타임아웃 경로를 재현하기도 어렵다.
 *
 * - idempotencyKey가 "FAIL_"로 시작 → 콜백 status=FAILED
 * - idempotencyKey가 "TIMEOUT_"로 시작 → 콜백을 영영 보내지 않음(webhook 자체가 안 오는 상황 시뮬레이션,
 *   #109 PAYMENT_REQUESTED 좀비상태 정책 검증용)
 * - 그 외 → 콜백 status=APPROVED
 *
 * 접두사 규칙은 테스트 코드와 (필요 시) Postman 시나리오에서 동일하게 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    private static final String FAIL_PREFIX = "FAIL_";
    private static final String TIMEOUT_PREFIX = "TIMEOUT_";

    private final PgWebhookCallbackSender callbackSender;

    @Override
    public PgRefundResult requestRefund(String pgTransactionId, Long amount) {

        if (pgTransactionId == null) {
            log.warn("[MockPG] pgTransactionId 없이 환불 요청이 들어왔습니다. 환불 실패로 처리합니다.");
            return PgRefundResult.failure("PG 거래 식별자가 없습니다.");
        }

        log.info("[MockPG] 환불 승인. pgTransactionId={}, amount={}", pgTransactionId, amount);
        return PgRefundResult.success();
    }

    @Override
    public String requestApprovalAsync(UUID orderId, String idempotencyKey, Long amount, PaymentMethod paymentMethod) {

        String pgTransactionId = "PG-" + UUID.randomUUID();

        // 타임 아웃
        if (idempotencyKey != null && idempotencyKey.startsWith(TIMEOUT_PREFIX)) {

            // webhook을 아예 보내지 않는다 — "PG가 요청을 접수했지만 결과를 영영 알려주지 않는" 상황 시뮬레이션.
            // 호출 시점엔 에러가 없고, 콜백이 영영 안 오는 형태로만 드러난다.
            log.warn("[MockPG] 비동기 타임아웃 시뮬레이션(webhook 미발송). orderId={}, pgTransactionId={}",
                    orderId, pgTransactionId);
            return pgTransactionId;
        }

        boolean willFail = idempotencyKey != null && idempotencyKey.startsWith(FAIL_PREFIX);
        PgWebhookRequest payload = willFail
                ? new PgWebhookRequest(pgTransactionId, orderId, "FAILED", amount, "잔액이 부족합니다.")
                : new PgWebhookRequest(pgTransactionId, orderId, "APPROVED", amount, null);

        log.info("[MockPG] 비동기 결제 승인 요청 접수. orderId={}, pgTransactionId={}, paymentMethod={}",
                orderId, pgTransactionId, paymentMethod);
        callbackSender.sendDelayed(payload);

        return pgTransactionId;
    }

    @Override
    public void requestRefundAsync(UUID orderId, String pgTransactionId, Long amount) {

        if (pgTransactionId == null) {
            log.warn("[MockPG] pgTransactionId 없이 비동기 환불 요청이 들어왔습니다. 콜백을 보내지 않습니다.");
            return;
        }

        log.info("[MockPG] 비동기 환불 요청 접수. orderId={}, pgTransactionId={}, amount={}",
                orderId, pgTransactionId, amount);
        callbackSender.sendDelayed(new PgWebhookRequest(pgTransactionId, orderId, "REFUNDED", amount, null));
    }
}