package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgTransactionStatus;
import com.fandom.order_service.payment.presentation.dto.request.PgWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock PG 클라이언트.
 *
 * 실제 PG 없이 로컬/테스트 환경에서 결제 흐름을 검증한다.
 * 시나리오는 랜덤이 아니라 idempotencyKey 규칙으로 결정된다.
 *
 * - FAIL_            : 결제 영구 실패 웹훅(FAILED)
 * - TIMEOUT_         : 결제 웹훅 미발송
 * - TRANSIENT_FAIL_  : 일시적 실패 웹훅(FAILED, failureReason="TRANSIENT:...").
 *                      재시도는 새 idempotencyKey로 오므로 prefix 없어 정상 승인 처리됨.
 * - REFUND_FAIL_     : 승인 후 환불 실패 웹훅(REFUND_FAILED)
 * - REFUND_TIMEOUT_  : 승인 후 환불 웹훅 미발송
 * - 그 외             : 승인(APPROVED), 환불(REFUNDED)
 *
 * 확률적 장애 주입 모드(CHAOS_MODE_ENABLED):
 * 위 마커는 기능 검증용 결정론적 시나리오라 부하 테스트에서 매 요청마다 지정할 수 없다.
 * 마커가 없는 요청에 한해 CHAOS_MODE_ENABLED가 켜져 있으면 확률적으로 결과를 흔든다
 * PG 호출 자체는 논블로킹이라 여기서 지연을 줘도 Tomcat 스레드는 잡히지 않는다 — 지연/실패는
 * webhook 도착 타이밍과 결과에만 영향을 주며, 실제 부하는 이 webhook을 order-service가
 * 수신·처리하는 시점(DB 락, 커넥션 풀, Kafka 발행)에 걸린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    private static final String FAIL_PREFIX = "FAIL_";
    private static final String TIMEOUT_PREFIX = "TIMEOUT_";
    private static final String TRANSIENT_FAIL_PREFIX = "TRANSIENT_FAIL_";
    private static final String TRANSIENT_FAILURE_REASON = "TRANSIENT:PG 일시적 오류";
    private static final String PAYMENT_FAILURE_REASON = "결제 실패";
    private static final String REFUND_FAIL_MARKER = "REFUND_FAIL_";
    private static final String REFUND_TIMEOUT_MARKER = "REFUND_TIMEOUT_";

    // 부하 테스트용 확률적 장애 주입 모드. 마커 없는 요청에만 적용.
    // 부하 테스트 시나리오 실행 전에 true로 바꿔서 재배포.
    private static final boolean CHAOS_MODE_ENABLED = false;
    private static final int CHAOS_FAILED_PERCENT = 3;  // 실패 webhook
    private static final int CHAOS_LOST_PERCENT = 2;    // 승인되지만 webhook 미발송(유실)
    private static final int CHAOS_SLOW_PERCENT = 10;   // 승인 + 지연 지터. 나머지 85는 정상
    private static final int CHAOS_ROLL_BOUND = 100;     // roll 범위: 0~99
    private static final long CHAOS_JITTER_MIN_MILLIS = 1_000L;
    private static final long CHAOS_JITTER_MAX_MILLIS = 3_000L;

    enum ChaosOutcome { NORMAL, SLOW, FAILED, LOST }

    private final MockPgWebhookCallbackSender callbackSender;
    private final MockPgTransactionRepository mockPgTransactionRepository;

    @Override
    @Transactional
    public String requestApprovalAsync(UUID orderId, String idempotencyKey, Long amount, PaymentMethod paymentMethod) {

        String pgTransactionId = "PG-" + refundScenarioMarker(idempotencyKey) + UUID.randomUUID();

        // 1단계: 마커로 결과가 이미 정해진 요청인지 확인한다.
        boolean isMarkedTransientFail = false;
        boolean isMarkedFail = false;
        boolean isMarkedTimeout = false;

        if (idempotencyKey != null) {
            if (idempotencyKey.startsWith(TRANSIENT_FAIL_PREFIX)) {
                isMarkedTransientFail = true;
            } else if (idempotencyKey.startsWith(FAIL_PREFIX)) {
                isMarkedFail = true;
            } else if (idempotencyKey.startsWith(TIMEOUT_PREFIX)) {
                isMarkedTimeout = true;
            }
        }
        boolean hasMarker = isMarkedTransientFail || isMarkedFail || isMarkedTimeout;

        // 2단계: 마커가 없고 확률모드가 켜져 있을 때만 주사위를 굴린다.
        ChaosOutcome chaos = ChaosOutcome.NORMAL;
        if (!hasMarker && CHAOS_MODE_ENABLED) {
            chaos = rollChaosOutcome();
        }

        // 3단계: 마커와 chaos 결과를 합쳐서, 이번 요청의 최종 결과를 하나로 정리한다.
        boolean isApproved;
        String failureReason = null;

        if (isMarkedTransientFail) {
            isApproved = false;
            failureReason = TRANSIENT_FAILURE_REASON;
        } else if (isMarkedFail || chaos == ChaosOutcome.FAILED) {
            isApproved = false;
            failureReason = PAYMENT_FAILURE_REASON;
        } else {
            isApproved = true;
        }

        boolean webhookLost = isMarkedTimeout || chaos == ChaosOutcome.LOST;

        // 4단계: PG 자체 거래 기록은 결과와 무관하게 항상 저장한다(진짜 상태).
        MockPgTransaction transaction = isApproved
                ? MockPgTransaction.approved(pgTransactionId, orderId, amount)
                : MockPgTransaction.failed(pgTransactionId, orderId, amount, failureReason);
        mockPgTransactionRepository.save(transaction);

        // 5단계: webhook이 유실되는 시나리오면 여기서 끝낸다.
        if (webhookLost) {
            log.warn("[MockPG] 비동기 타임아웃/유실 시뮬레이션(webhook 미발송). orderId={}, pgTransactionId={}, chaos={}",
                    orderId, pgTransactionId, chaos);
            return pgTransactionId;
        }

        // 6단계: webhook에 담을 내용을 만든다.
        String status = isApproved ? "APPROVED" : "FAILED";
        PgWebhookRequest payload = new PgWebhookRequest(pgTransactionId, orderId, status, amount, failureReason);

        log.info("[MockPG] 비동기 결제 승인 요청 접수. orderId={}, pgTransactionId={}, paymentMethod={}, chaos={}",
                orderId, pgTransactionId, paymentMethod, chaos);

        // 7단계: SLOW면 지연을 추가해서 보내고, 아니면 평소대로 보낸다.
        if (chaos == ChaosOutcome.SLOW) {
            callbackSender.sendDelayed(payload, randomJitterMillis());
        } else {
            callbackSender.sendDelayed(payload);
        }

        return pgTransactionId;
    }

    @Override
    @Transactional
    public void requestRefundAsync(UUID orderId, String pgTransactionId, Long amount) {

        if (pgTransactionId == null) {
            log.warn("[MockPG] pgTransactionId 없이 비동기 환불 요청이 들어왔습니다. 콜백을 보내지 않습니다.");
            return;
        }

        MockPgTransaction transaction = mockPgTransactionRepository.findByPgTransactionIdForUpdate(pgTransactionId)
                .orElse(null);
        if (transaction == null) {
            // 정상 흐름이라면 결제 승인 시점에 이미 저장돼 있어야 한다. 데이터 불일치 방어용 로그.
            log.error("[MockPG] 거래 기록을 찾을 수 없어 환불 처리를 건너뜁니다. pgTransactionId={}, orderId={}",
                    pgTransactionId, orderId);
            return;
        }

        // 1단계: 마커 확인
        boolean isMarkedFail = pgTransactionId.contains(REFUND_FAIL_MARKER);
        boolean isMarkedTimeout = pgTransactionId.contains(REFUND_TIMEOUT_MARKER);
        boolean hasMarker = isMarkedFail || isMarkedTimeout;

        // 2단계: 마커가 없을 때만 주사위
        ChaosOutcome chaos = ChaosOutcome.NORMAL;
        if (!hasMarker && CHAOS_MODE_ENABLED) {
            chaos = rollChaosOutcome();
        }

        // 3단계: 최종 결과 정리
        boolean isRefundFailed = isMarkedFail || chaos == ChaosOutcome.FAILED;
        boolean webhookLost = isMarkedTimeout || chaos == ChaosOutcome.LOST;

        // 4단계: 거래 상태를 먼저 갱신한다(진짜 상태). webhook 발송 여부와 무관하다.
        if (isRefundFailed) {
            transaction.markRefundFailed("PG 환불 처리 중 오류가 발생했습니다.");
        } else {
            transaction.markRefunded();
        }

        // 5단계: webhook이 유실되는 시나리오면 여기서 끝낸다.
        if (webhookLost) {
            log.warn("[MockPG] 환불 비동기 타임아웃/유실 시뮬레이션(webhook 미발송). orderId={}, pgTransactionId={}, chaos={}",
                    orderId, pgTransactionId, chaos);
            return;
        }

        // 6단계: webhook 내용을 만든다.
        PgWebhookRequest payload = isRefundFailed
                ? new PgWebhookRequest(pgTransactionId, orderId, "REFUND_FAILED", amount, "PG 환불 처리 중 오류가 발생했습니다.")
                : new PgWebhookRequest(pgTransactionId, orderId, "REFUNDED", amount, null);

        log.info("[MockPG] 비동기 환불 요청 접수. orderId={}, pgTransactionId={}, amount={}, chaos={}",
                orderId, pgTransactionId, amount, chaos);

        // 7단계: SLOW면 지연을 추가해서 보내고, 아니면 평소대로 보낸다.
        if (chaos == ChaosOutcome.SLOW) {
            callbackSender.sendDelayed(payload, randomJitterMillis());
        } else {
            callbackSender.sendDelayed(payload);
        }
    }

    @Override
    public Optional<PgTransactionStatus> inquireTransaction(String pgTransactionId) {

        return mockPgTransactionRepository.findByPgTransactionId(pgTransactionId)
                .map(t -> new PgTransactionStatus(
                        t.getPgTransactionId(), t.getOrderId(), t.getAmount(), t.getStatus(), t.getFailureReason()));
    }

    /**
     * 환불 시나리오 재현을 위해 idempotencyKey의 마커를
     * pgTransactionId에 포함시킨다.
     *
     * 환불 요청 시에는 pgTransactionId만 전달되므로
     * 승인 시점에 마커를 미리 저장해둔다.
     */
    private String refundScenarioMarker(String idempotencyKey) {
        if (idempotencyKey == null) {
            return "";
        }
        if (idempotencyKey.contains(REFUND_FAIL_MARKER)) {
            return REFUND_FAIL_MARKER;
        }
        if (idempotencyKey.contains(REFUND_TIMEOUT_MARKER)) {
            return REFUND_TIMEOUT_MARKER;
        }
        return "";
    }

    private ChaosOutcome rollChaosOutcome() {
        int roll = ThreadLocalRandom.current().nextInt(CHAOS_ROLL_BOUND); // 0 이상 99 이하 정수
        return resolveChaosOutcome(roll);
    }

    /**
     * roll(0~99)을 누적 확률 구간에 매핑한다. 정수 비교라 부동소수점 오차가 없다.
     * 순수 함수로 분리해서 랜덤 시드 없이 경계값 테스트가 가능하도록 함.
     */
    static ChaosOutcome resolveChaosOutcome(int roll) {
        int failedBoundary = CHAOS_FAILED_PERCENT;                // 3
        int lostBoundary = failedBoundary + CHAOS_LOST_PERCENT;   // 5
        int slowBoundary = lostBoundary + CHAOS_SLOW_PERCENT;     // 15

        if (roll < failedBoundary) {
            return ChaosOutcome.FAILED;
        }
        if (roll < lostBoundary) {
            return ChaosOutcome.LOST;
        }
        if (roll < slowBoundary) {
            return ChaosOutcome.SLOW;
        }
        return ChaosOutcome.NORMAL;
    }

    private long randomJitterMillis() {
        // CHAOS_JITTER_MAX_MILLIS 값도 나오게 하려고 +1 (nextLong의 두 번째 인자는 포함 안 됨)
        return ThreadLocalRandom.current().nextLong(CHAOS_JITTER_MIN_MILLIS, CHAOS_JITTER_MAX_MILLIS + 1);
    }
}
