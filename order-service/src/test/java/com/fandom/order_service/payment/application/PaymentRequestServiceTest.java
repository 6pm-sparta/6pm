package com.fandom.order_service.payment.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.application.request.PaymentRequestResult;
import com.fandom.order_service.payment.application.request.PaymentRequestService;
import com.fandom.order_service.payment.application.request.PaymentRequestWriter;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.presentation.dto.request.PaymentRequest;
import com.fandom.order_service.payment.presentation.dto.response.PaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRequestService 단위 테스트")
class PaymentRequestServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private PaymentRequestWriter paymentRequestWriter;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    private PaymentRequestService paymentRequestService;

    private PaymentRequest request;
    private UUID orderId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties(
                new OrderProperties.Hold(30L, 600L), 10,
                new OrderProperties.PaymentLockProperties(3L, 5L, 600L), null,
                new OrderProperties.Cancellation(24L),
                new OrderProperties.Compensation(3, 1000L), null, null, null);

        paymentRequestService = new PaymentRequestService(
                redissonClient, redisTemplate, objectMapper, paymentRequestWriter,
                paymentRepository, paymentGateway, orderProperties);

        orderId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        request = new PaymentRequest(orderId, PaymentMethod.CARD);
    }

    private Payment requestedPaymentWithId(UUID paymentId) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.REQUESTED)
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey("idem-key")
                .build();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        ReflectionTestUtils.setField(payment, "createdAt", LocalDateTime.now());
        return payment;
    }

    private void givenLockAcquiredAndMarkerClaimed() throws InterruptedException {
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(3L, 5L, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(valueOperations.setIfAbsent(anyString(), eq("IN_PROGRESS"), any(Duration.class))).willReturn(true);
    }

    @Test
    @DisplayName("멱등성 캐시가 없으면 PG에 비동기 승인을 요청하고 REQUESTED 상태로 신규 처리(true) 응답한다")
    void requestPayment_newRequest_success() throws InterruptedException {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null); // 캐시 없음(락 밖, 락 안 두 번 다 호출됨)
        givenLockAcquiredAndMarkerClaimed();

        UUID paymentId = UUID.randomUUID();
        Payment requestedPayment = requestedPaymentWithId(paymentId);
        given(paymentRequestWriter.markPaymentRequestedAndSave(orderId, requesterId, PaymentMethod.CARD, "idem-key-success"))
                .willReturn(requestedPayment);

        given(paymentGateway.requestApprovalAsync(orderId, "idem-key", 50_000L, PaymentMethod.CARD))
                .willReturn("PG-9999");

        // recordPgTransactionId는 Writer 내부에서 일어나는 일이라 mock이라 실제 반영 안 됨 —
        // "기록 이후 다시 조회하면 pgTransactionId가 박혀있다"는 사실만 stub으로 흉내낸다.
        Payment withPgTransactionId = requestedPaymentWithId(paymentId);
        withPgTransactionId.recordPgTransactionId("PG-9999");
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(withPgTransactionId));

        // when
        PaymentRequestResult result = paymentRequestService.requestPayment(request, requesterId, "idem-key-success");

        // then
        assertThat(result.newlyProcessed()).isTrue();
        assertThat(result.payment().paymentStatus()).isEqualTo("REQUESTED"); // 승인 여부는 아직 모름 — webhook 몫
        assertThat(result.payment().pgTransactionId()).isEqualTo("PG-9999");
        verify(lock).unlock();
        verify(paymentRequestWriter).recordPgTransactionId(paymentId, "PG-9999");
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class)); // REQUESTED 스냅샷 캐싱됨
    }

    @Test
    @DisplayName("멱등성 캐시(락 밖 조회)에 이미 결과가 있으면 락을 잡지 않고 그대로 반환한다(멱등 응답, false)")
    void requestPayment_earlyCacheHit_skipsLock() throws Exception {
        // given
        PaymentResponse cachedResponse = new PaymentResponse(
                UUID.randomUUID(), orderId, 50_000L, "REQUESTED", "CARD", "PG-1111", LocalDateTime.now());
        String json = objectMapper.writeValueAsString(cachedResponse);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(json);

        // when
        PaymentRequestResult result = paymentRequestService.requestPayment(request, requesterId, "idem-key-cached");

        // then
        assertThat(result.newlyProcessed()).isFalse();
        assertThat(result.payment().pgTransactionId()).isEqualTo("PG-1111");
        verify(redissonClient, never()).getLock(anyString());
        verify(paymentGateway, never()).requestApprovalAsync(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("같은 멱등키로 처리 중(IN_PROGRESS, 다른 요청 소유)인 요청이 또 들어오면 PAYMENT_IN_PROGRESS 예외가 발생하고 마커는 지우지 않는다")
    void requestPayment_inProgress_throwsPaymentInProgress() throws InterruptedException {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(3L, 5L, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        // 같은 키로 이미 처리 중(다른 요청이 점유) → setIfAbsent가 false
        given(valueOperations.setIfAbsent(anyString(), eq("IN_PROGRESS"), any(Duration.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentRequestService.requestPayment(request, requesterId, "idem-key-in-progress"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_IN_PROGRESS);

        verify(lock).unlock(); // 예외가 나도 락은 반드시 해제돼야 한다
        // 이 마커는 우리 게 아니라 먼저 들어온 요청 소유 — 우리가 지우면 그 요청의 IN_PROGRESS 보장이 깨진다.
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("분산락 획득에 실패하면 LOCK_ACQUISITION_FAILED 예외가 발생한다")
    void requestPayment_lockAcquisitionFails_throws() throws InterruptedException {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(3L, 5L, TimeUnit.SECONDS)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentRequestService.requestPayment(request, requesterId, "idem-key-lock-fail"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.LOCK_ACQUISITION_FAILED);
    }

    @Test
    @DisplayName("#107: markPaymentRequestedAndSave가 실패하면(DB 검증 실패 등) 우리가 점유한 IN_PROGRESS 마커를 지운다")
    void requestPayment_writerFails_clearsOwnMarker() throws InterruptedException {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        givenLockAcquiredAndMarkerClaimed();

        given(paymentRequestWriter.markPaymentRequestedAndSave(orderId, requesterId, PaymentMethod.CARD, "idem-key-writer-fail"))
                .willThrow(new CustomException(PaymentErrorCode.INVALID_ORDER_STATUS));

        // when & then
        assertThatThrownBy(() -> paymentRequestService.requestPayment(request, requesterId, "idem-key-writer-fail"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.INVALID_ORDER_STATUS);

        verify(lock).unlock();
        verify(redisTemplate).delete(anyString()); // 마커는 우리 것이었으니 반드시 지운다
        verify(paymentGateway, never()).requestApprovalAsync(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("#107: PG 접수(ack) 자체가 실패하면 IN_PROGRESS 마커를 지우고 PG_ERROR를 던진다")
    void requestPayment_pgAckFails_clearsMarkerAndThrowsPgError() throws InterruptedException {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        givenLockAcquiredAndMarkerClaimed();

        UUID paymentId = UUID.randomUUID();
        Payment requestedPayment = requestedPaymentWithId(paymentId);
        given(paymentRequestWriter.markPaymentRequestedAndSave(orderId, requesterId, PaymentMethod.CARD, "idem-key-pg-ack-fail"))
                .willReturn(requestedPayment);

        given(paymentGateway.requestApprovalAsync(orderId, "idem-key", 50_000L, PaymentMethod.CARD))
                .willThrow(new RuntimeException("PG 접속 실패"));

        // when & then
        assertThatThrownBy(() -> paymentRequestService.requestPayment(request, requesterId, "idem-key-pg-ack-fail"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PG_ERROR);

        verify(redisTemplate).delete(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class)); // 캐싱 안 됨
    }
}
