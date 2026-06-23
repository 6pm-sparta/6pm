package com.fandom.order_service.payment.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.payment.application.request.PaymentRequestResult;
import com.fandom.order_service.payment.application.request.PaymentRequestService;
import com.fandom.order_service.payment.application.request.PaymentRequestWriter;
import com.fandom.order_service.kafka.producer.OrderEventProducer;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgApprovalResult;
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

    @Mock
    private OrderEventProducer orderEventProducer;

    private PaymentRequestService paymentRequestService;

    private PaymentRequest request;
    private UUID orderId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties(
                new OrderProperties.Hold(30L, 600L), 10,
                new OrderProperties.PaymentLockProperties(3L, 5L, 600L),
                new OrderProperties.Cancellation(24L),
                new OrderProperties.Compensation(3, 1000L), null);

        paymentRequestService = new PaymentRequestService(
                redissonClient, redisTemplate, objectMapper, paymentRequestWriter,
                paymentRepository, paymentGateway, orderProperties, orderEventProducer);

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

    @Test
    @DisplayName("멱등성 캐시가 없으면 분산락을 획득해 결제를 처리하고 신규 처리(true)로 응답한다")
    void requestPayment_newRequest_success() throws InterruptedException {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null); // 캐시 없음(락 밖, 락 안 두 번 다 호출됨)

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(3L, 5L, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        given(valueOperations.setIfAbsent(anyString(), eq("IN_PROGRESS"), any(Duration.class))).willReturn(true);

        UUID paymentId = UUID.randomUUID();
        Payment requestedPayment = requestedPaymentWithId(paymentId);
        given(paymentRequestWriter.markPaymentRequestedAndSave(orderId, requesterId, PaymentMethod.CARD, "idem-key-success"))
                .willReturn(requestedPayment);

        given(paymentGateway.requestApproval("idem-key", 50_000L, PaymentMethod.CARD))
                .willReturn(PgApprovalResult.approved("PG-9999"));

        Payment approvedPayment = requestedPaymentWithId(paymentId);
        approvedPayment.approve("PG-9999");
        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(approvedPayment));

        // when
        PaymentRequestResult result = paymentRequestService.requestPayment(request, requesterId, "idem-key-success");

        // then
        assertThat(result.newlyProcessed()).isTrue();
        assertThat(result.payment().pgTransactionId()).isEqualTo("PG-9999");
        verify(lock).unlock();
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class)); // 결과 캐싱됨
        verify(orderEventProducer).publishPaymentCompleted(orderId);
    }

    @Test
    @DisplayName("멱등성 캐시(락 밖 조회)에 이미 결과가 있으면 락을 잡지 않고 그대로 반환한다(멱등 응답, false)")
    void requestPayment_earlyCacheHit_skipsLock() throws Exception {
        // given
        PaymentResponse cachedResponse = new PaymentResponse(
                UUID.randomUUID(), orderId, 50_000L, "APPROVED", "CARD", "PG-1111", LocalDateTime.now());
        String json = objectMapper.writeValueAsString(cachedResponse);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(json);

        // when
        PaymentRequestResult result = paymentRequestService.requestPayment(request, requesterId, "idem-key-cached");

        // then
        assertThat(result.newlyProcessed()).isFalse();
        assertThat(result.payment().pgTransactionId()).isEqualTo("PG-1111");
        verify(redissonClient, never()).getLock(anyString());
        verify(orderEventProducer, never()).publishPaymentCompleted(any());
    }

    @Test
    @DisplayName("같은 멱등키로 처리 중(IN_PROGRESS)인 요청이 또 들어오면 PAYMENT_IN_PROGRESS 예외가 발생한다")
    void requestPayment_inProgress_throwsPaymentInProgress() throws InterruptedException {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(3L, 5L, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        // 같은 키로 이미 처리 중 → setIfAbsent가 false
        given(valueOperations.setIfAbsent(anyString(), eq("IN_PROGRESS"), any(Duration.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentRequestService.requestPayment(request, requesterId, "idem-key-in-progress"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_IN_PROGRESS);

        verify(lock).unlock(); // 예외가 나도 락은 반드시 해제돼야 한다
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
    @DisplayName("PG가 거절(DECLINED)하면 결제를 FAILED로 반영하고 PG_ERROR(502) 예외를 던진다")
    void requestPayment_pgDeclined_throwsPgError() throws InterruptedException {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(3L, 5L, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(valueOperations.setIfAbsent(anyString(), eq("IN_PROGRESS"), any(Duration.class))).willReturn(true);

        UUID paymentId = UUID.randomUUID();
        Payment requestedPayment = requestedPaymentWithId(paymentId);
        given(paymentRequestWriter.markPaymentRequestedAndSave(orderId, requesterId, PaymentMethod.CARD, "idem-key-declined"))
                .willReturn(requestedPayment);

        given(paymentGateway.requestApproval("idem-key", 50_000L, PaymentMethod.CARD))
                .willReturn(PgApprovalResult.declined("잔액이 부족합니다."));

        // when & then
        assertThatThrownBy(() -> paymentRequestService.requestPayment(request, requesterId, "idem-key-declined"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PG_ERROR);

        verify(paymentRequestWriter).applyFailure(orderId, paymentId, "잔액이 부족합니다.");
        verify(orderEventProducer).publishPaymentFailed(orderId);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class)); // 실패는 캐싱 안 함
    }
}