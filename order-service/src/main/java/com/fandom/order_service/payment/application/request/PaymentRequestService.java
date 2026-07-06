package com.fandom.order_service.payment.application.request;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
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
 * 결제 요청. 락 획득 순서(order-service 설계 문서 6. 동시성 제어 전략):
 *
 * 1. Redis 분산락(Redisson RLock) 획득 — 인스턴스 간 동시 결제 요청 차단
 * 2. Redis 멱등성 키(Idempotency-Key) 확인 — 분산락 안에서, 재시도/중복 클릭 시 기존 결과 그대로 반환
 * 3-4. orders 비관적 락 + 상태 검증(PENDING) + 진행중 결제(payments.REQUESTED) 존재 여부 확인
 *      + 결제 시도 레코드 생성, 커밋
 * 5. 분산락 해제
 * 6. PG에 비동기 승인 요청 — "접수됐다"는 사실과 pgTransactionId만 즉시 받는다.
 *    실제 승인/거절은 PgWebhookService가 콜백으로 받아 반영한다.
 * 7. pgTransactionId를 Payment에 기록, REQUESTED 상태로 응답 캐싱 후 즉시 응답 반환.
 *
 * 분산락은 PG 호출 전체를 감싸지 않는다. 4번에서 orders.status는 PENDING 그대로 두고
 * payments에 REQUESTED 레코드가 새로 생기므로, 락이 풀린 뒤 들어오는 동시 요청은 3번 단계의
 * existsByOrderIdAndPaymentStatus(REQUESTED) 체크에서 자연히 거부된다(락은 1차 방어,
 * payments 테이블 상태는 PG 호출 구간 전체를 덮는 2차 방어로 역할 분리).
 *
 * 멱등성 키 캐시는 세 가지 값 중 하나를 가진다:
 * - 키 자체가 없음: 처음 들어온 요청
 * - "IN_PROGRESS": 같은 키로 처리가 진행 중(마킹 이후 캐싱 이전 구간) — 결과가 아직 없으므로 409.
 * - PaymentResponse의 JSON: 처리가 끝남(REQUESTED 상태 스냅샷) — 그대로 캐시된 결과를 200으로 반환.
 *   비동기 모델에서는 최종 승인 여부가 아니라 "요청이 접수됐다"는 스냅샷을 캐싱한다 — 그 이후의
 *   APPROVED/FAILED 전이는 webhook 쪽 책임이고, 클라이언트는 GET /payments/{id}로 최신 상태를 조회한다.
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

            // 락 획득 전후 경쟁 상황을 고려해 멱등성 키를 재확인한다.
            Optional<PaymentResponse> cached = readCachedResult(idemKey);
            if (cached.isPresent()) {
                return new PaymentRequestResult(cached.get(), false);
            }

            // 같은 idempotencyKey의 중복 처리를 방지하기 위해 처리 중 상태를 마킹한다.
            markInProgressOrThrow(idemKey);

            try {
                // 주문 상태 전이 및 Payment 생성
                payment = paymentRequestWriter.markPaymentRequestedAndSave(
                        request.orderId(), requesterId, request.paymentMethod(), idempotencyKey);
            } catch (RuntimeException writerFailure) {
                //후속 처리 실패 시 마커 정리
                clearInProgressMarker(idemKey);
                throw writerFailure;
            }

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // 여기부터는 락 밖. PG 호출은 외부 API라 느릴 수 있어 락을 들고 있지 않는다.
        PaymentResponse response = requestApprovalAndCache(payment, idemKey);
        return new PaymentRequestResult(response, true);
    }

    private PaymentResponse requestApprovalAndCache(Payment payment, String idemKey) {

        try {
            // PG 요청을 접수하고 거래 식별자를 저장한다.
            // 실제 승인/거절은 webhook으로 비동기 반영된다.
            String pgTransactionId = paymentGateway.requestApprovalAsync(
                    payment.getOrderId(), payment.getIdempotencyKey(), payment.getAmount(), payment.getPaymentMethod());
            paymentRequestWriter.recordPgTransactionId(payment.getId(), pgTransactionId);
        } catch (RuntimeException failure) {
            // 접수 실패 시 멱등성 마커 정리
            clearInProgressMarker(idemKey);
            if (failure instanceof CustomException) {
                throw failure;
            }
            throw new CustomException(PaymentErrorCode.PG_ERROR);
        }

        Payment requested = paymentRepository.findById(payment.getId()).orElse(payment);
        PaymentResponse response = PaymentResponse.from(requested);
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
