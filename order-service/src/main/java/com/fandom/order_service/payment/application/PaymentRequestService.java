package com.fandom.order_service.payment.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.kafka.producer.OrderEventProducer;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgApprovalResult;
import com.fandom.order_service.payment.presentation.dto.request.PaymentRequest;
import com.fandom.order_service.payment.presentation.dto.response.PaymentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 결제 요청/승인. 락 획득 순서(order-service 설계 문서 6. 동시성 제어 전략):
 *
 * 1. Redis 분산락(Redisson RLock) 획득 — 인스턴스 간 동시 결제 요청 차단
 * 2. Redis 멱등성 키(Idempotency-Key) 확인 — 분산락 안에서, 재시도/중복 클릭 시 기존 결과 그대로 반환
 * 3-4. orders 비관적 락 + 상태 검증(PENDING) + PAYMENT_REQUESTED 전이 + 결제 시도 레코드 생성, 커밋
 * 5. 분산락 해제
 * 6. PG API 호출 (락 없이, 동기, MVP)
 * 7. 결과 반영(PAID/FAILED) — 별도 트랜잭션, 커밋 후 order.payment.completed/failed 발행(#87)
 *
 * 분산락은 PG 호출 전체를 감싸지 않는다. 4번에서 상태가 이미 PAYMENT_REQUESTED로 바뀌었으므로,
 * 락이 풀린 뒤 들어오는 동시 요청은 3번 단계의 상태 검증에서 자연히 거부된다(락은 1차 방어,
 * DB 상태값은 PG 호출 구간 전체를 덮는 2차 방어로 역할 분리).
 *
 * 멱등성 키 캐시는 세 가지 값 중 하나를 가진다:
 * - 키 자체가 없음: 처음 들어온 요청
 * - "IN_PROGRESS": 같은 키로 처리가 진행 중(PG 호출 전이거나 호출 중) — 결과가 아직 없으므로 409
 * - PaymentResponse의 JSON: 처리가 끝남 — 그대로 캐시된 결과를 200으로 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRequestService {

    private static final String LOCK_KEY_PREFIX = "payment:lock:order:";
    private static final String IDEMPOTENCY_KEY_PREFIX = "payment:idem:";
    private static final String IN_PROGRESS_MARKER = "IN_PROGRESS";

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentRequestWriter paymentRequestWriter;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final OrderProperties orderProperties;
    private final OrderEventProducer orderEventProducer;

    public PaymentRequestResult requestPayment(PaymentRequest request, UUID requesterId, String idempotencyKey) {

        String idemKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        Optional<PaymentResponse> earlyCached = readCachedResult(idemKey);
        if (earlyCached.isPresent()) {
            return new PaymentRequestResult(earlyCached.get(), false);
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + request.orderId());
        boolean acquired;
        try {
            acquired = lock.tryLock(
                    orderProperties.paymentLockProperties().lockWaitSeconds(),
                    orderProperties.paymentLockProperties().lockHoldSeconds(),
                    TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CustomException(PaymentErrorCode.LOCK_ACQUISITION_FAILED);
        }

        if (!acquired) {
            throw new CustomException(PaymentErrorCode.LOCK_ACQUISITION_FAILED);
        }

        Payment payment;
        try {

            // 락 안에서 멱등성 키 재확인. 락 밖 조회와 사이 타이밍에 다른 요청이 끝났을 수 있다.
            Optional<PaymentResponse> cached = readCachedResult(idemKey);
            if (cached.isPresent()) {
                return new PaymentRequestResult(cached.get(), false);
            }

            // 같은 idempotencyKey로 "처리 중"임을 먼저 마킹. 결과 캐싱 전에 같은 키로 또 들어오면
            // PAYMENT_IN_PROGRESS로 막아야 한다(결과가 아직 없는데 PG를 두 번 호출하는 걸 방지).
            markInProgressOrThrow(idemKey);

            // 비관적 락 + 본인 확인 + 상태 검증 + PAYMENT_REQUESTED 전이 + Payment(REQUESTED) 저장 — 짧은 트랜잭션
            payment = paymentRequestWriter.markPaymentRequestedAndSave(
                    request.orderId(), requesterId, request.paymentMethod(), idempotencyKey);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // 여기부터는 락 밖. PG 호출은 외부 API라 느릴 수 있어 락을 들고 있지 않는다.
        PaymentResponse response = callPgAndApplyResult(payment, idemKey);
        return new PaymentRequestResult(response, true);
    }

    private PaymentResponse callPgAndApplyResult(Payment payment, String idemKey) {

        PgApprovalResult pgResult = paymentGateway.requestApproval(
                payment.getIdempotencyKey(), payment.getAmount(), payment.getPaymentMethod());

        if (!pgResult.isApproved()) {
            String reason = pgResult.failureReason() != null ? pgResult.failureReason() : "PG 응답 타임아웃";
            paymentRequestWriter.applyFailure(payment.getOrderId(), payment.getId(), reason);
            orderEventProducer.publishPaymentFailed(payment.getOrderId());

            // DECLINED/TIMEOUT 모두 502로 처리.
            // 실패는 캐싱하지 않는다 — 멱등키 TTL 동안 같은 키로 재시도하면 PG를 다시 호출해야
            // 재시도 로직(P1, 멱등성 키 동일 유지)이 의미를 갖는다. 대신 IN_PROGRESS 마커는 지운다.
            clearInProgressMarker(idemKey);
            throw new CustomException(PaymentErrorCode.PG_ERROR);
        }

        paymentRequestWriter.applyApproval(payment.getOrderId(), payment.getId(), pgResult.pgTransactionId());
        orderEventProducer.publishPaymentCompleted(payment.getOrderId());

        Payment approved = paymentRepository.findById(payment.getId()).orElse(payment);
        PaymentResponse response = PaymentResponse.from(approved);
        cacheResult(idemKey, response);
        return response;
    }

    private Optional<PaymentResponse> readCachedResult(String idemKey) {
        String cached;
        try {
            cached = redisTemplate.opsForValue().get(idemKey);
        } catch (DataAccessException redisDown) {
            // Redis 장애 시 멱등성 캐시 조회를 건너뛴다. 신규 요청처럼 처리되어 PG가 다시 호출될
            // 위험이 있지만, idempotencyKey가 PG 쪽에도 전달되므로 PG 레벨 멱등성에 기댄다(2차 방어).
            log.warn("[결제 요청] Redis 장애로 멱등성 캐시 조회를 건너뜁니다. idemKey={}", idemKey, redisDown);
            return Optional.empty();
        }

        if (cached == null || IN_PROGRESS_MARKER.equals(cached)) {
            // null=신규 요청, IN_PROGRESS=아직 결과 없음. 둘 다 "캐시된 결과 없음"으로 취급하고
            // 호출 측(markInProgressOrThrow)이 IN_PROGRESS 케이스를 구분해 409로 처리한다.
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(cached, PaymentResponse.class));
        } catch (JsonProcessingException malformed) {
            log.error("[결제 요청] 멱등성 캐시 역직렬화 실패. idemKey={}, raw={}", idemKey, cached, malformed);
            return Optional.empty();
        }
    }

    private void cacheResult(String idemKey, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                    idemKey, json, Duration.ofSeconds(orderProperties.paymentLockProperties().idempotencyKeyTtlSeconds()));
        } catch (JsonProcessingException neverExpected) {
            // PaymentResponse는 record + 기본 타입만으로 구성되어 직렬화 실패는 사실상 발생하지 않는다.
            log.error("[결제 요청] 결제 결과 직렬화 실패. 캐싱 없이 응답만 반환합니다. paymentId={}",
                    response.paymentId(), neverExpected);
        } catch (DataAccessException redisDown) {
            log.warn("[결제 요청] 결제 결과 캐싱 실패(Redis 장애). 다음 재시도는 PG 멱등키로 방어됩니다. paymentId={}",
                    response.paymentId(), redisDown);
        }
    }

    /**
     * Redis SETNX 자체는 try/catch로 장애만 격리하고, 그 결과를 바탕으로 한 비즈니스 판단
     * (이미 처리 중이면 409)은 try 블록 밖에서 한다.
     */
    private void markInProgressOrThrow(String idemKey) {
        Boolean claimed;
        try {
            claimed = redisTemplate.opsForValue().setIfAbsent(
                    idemKey, IN_PROGRESS_MARKER,
                    Duration.ofSeconds(orderProperties.paymentLockProperties().idempotencyKeyTtlSeconds()));
        } catch (DataAccessException redisDown) {
            log.warn("[결제 요청] Redis 장애로 멱등성 키 마킹 실패. idempotencyKey={}", idemKey, redisDown);
            return; // 1차 방어 생략, 결제 흐름은 그대로 진행(2차 방어는 PG 자체 멱등키에 의존)
        }

        if (Boolean.FALSE.equals(claimed)) {
            throw new CustomException(PaymentErrorCode.PAYMENT_IN_PROGRESS);
        }
    }

    private void clearInProgressMarker(String idemKey) {
        try {
            redisTemplate.delete(idemKey);
        } catch (DataAccessException redisDown) {
            log.warn("[결제 요청] 멱등성 키 해제 실패(Redis 장애). TTL 만료까지 자동 해제됨. idemKey={}", idemKey, redisDown);
        }
    }
}