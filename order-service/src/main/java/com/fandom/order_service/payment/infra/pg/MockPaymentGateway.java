package com.fandom.order_service.payment.infra.pg;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Mock PG 클라이언트. 실제 PG사 없이 로컬/테스트 환경에서 결제 흐름을 검증하기 위한 구현체.
 *
 * 시나리오는 임의(랜덤)가 아니라 idempotencyKey 접두사로 결정론적으로 트리거한다.
 * 랜덤 실패를 쓰면 테스트가 불안정(flaky)해지고, 특정 실패/타임아웃 경로를 재현하기도 어렵다.
 *
 * - idempotencyKey가 "FAIL_"로 시작 → DECLINED (잔액 부족 등 PG 거절 시뮬레이션)
 * - idempotencyKey가 "TIMEOUT_"로 시작 → TIMEOUT (PG 응답 없음 시뮬레이션)
 * - 그 외 → APPROVED
 *
 * 접두사 규칙은 테스트 코드와 (필요 시) Postman 시나리오에서 동일하게 사용한다.
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final String FAIL_PREFIX = "FAIL_";
    private static final String TIMEOUT_PREFIX = "TIMEOUT_";

    @Override
    public PgApprovalResult requestApproval(String idempotencyKey, Long amount, PaymentMethod paymentMethod) {

        if (idempotencyKey != null && idempotencyKey.startsWith(TIMEOUT_PREFIX)) {
            log.warn("[MockPG] 타임아웃 시뮬레이션. idempotencyKey={}", idempotencyKey);
            return PgApprovalResult.timeout();
        }

        if (idempotencyKey != null && idempotencyKey.startsWith(FAIL_PREFIX)) {
            log.info("[MockPG] 결제 거절 시뮬레이션. idempotencyKey={}", idempotencyKey);
            return PgApprovalResult.declined("잔액이 부족합니다.");
        }

        String pgTransactionId = "PG-" + UUID.randomUUID();
        log.info("[MockPG] 결제 승인. amount={}, paymentMethod={}, pgTransactionId={}",
                amount, paymentMethod, pgTransactionId);

        return PgApprovalResult.approved(pgTransactionId);
    }

    @Override
    public PgRefundResult requestRefund(String pgTransactionId, Long amount) {

        if (pgTransactionId == null) {
            log.warn("[MockPG] pgTransactionId 없이 환불 요청이 들어왔습니다. 환불 실패로 처리합니다.");
            return PgRefundResult.failure("PG 거래 식별자가 없습니다.");
        }

        log.info("[MockPG] 환불 승인. pgTransactionId={}, amount={}", pgTransactionId, amount);
        return PgRefundResult.success();
    }
}
