package com.fandom.order_service.order.application;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.kafka.producer.OrderEventProducer;
import com.fandom.order_service.order.application.compensation.OrderCompensationResult;
import com.fandom.order_service.order.application.compensation.OrderCompensationService;
import com.fandom.order_service.order.application.compensation.OrderCompensationWriter;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgRefundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompensationService 단위 테스트")
class OrderCompensationServiceTest {

    @Mock
    private OrderCompensationWriter orderCompensationWriter;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private OrderEventProducer orderEventProducer;

    private OrderCompensationService orderCompensationService;

    private UUID orderId;
    private UUID userId;
    private Payment payment;

    @BeforeEach
    void setUp() {
        // 테스트가 실제로 sleep 때문에 느려지지 않도록 backoff는 0으로 둔다.
        OrderProperties orderProperties = new OrderProperties(
                null, 10, null, null,
                new OrderProperties.Compensation(3, 0L), null);
        orderCompensationService = new OrderCompensationService(
                orderCompensationWriter, paymentGateway, orderEventProducer, orderProperties);

        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CARD)
                .pgTransactionId("PG-1234")
                .idempotencyKey("idem-key")
                .build();
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("이미 처리된 주문(ALREADY_HANDLED)이면 PG를 호출하지 않는다")
    void compensate_alreadyHandled_doesNotCallPg() {
        // given
        given(orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .willReturn(OrderCompensationResult.alreadyHandled(orderId, OrderStatus.REFUND_REQUESTED));

        // when
        orderCompensationService.compensate(orderId, "좌석 매진");

        // then
        verify(paymentGateway, never()).requestRefund(any(), any());
        verify(orderEventProducer, never()).publishOrderCancelledNotification(any(), any());
    }

    @Test
    @DisplayName("COMPENSATING으로 전이할 수 없는 상태(SKIPPED_INVALID_STATE)면 PG를 호출하지 않는다")
    void compensate_skippedInvalidState_doesNotCallPg() {
        // given
        given(orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .willReturn(OrderCompensationResult.skippedInvalidState(orderId, OrderStatus.PENDING));

        // when
        orderCompensationService.compensate(orderId, "좌석 매진");

        // then
        verify(paymentGateway, never()).requestRefund(any(), any());
    }

    @Test
    @DisplayName("첫 시도에 환불 성공하면 재시도 없이 즉시 REFUNDED 처리하고 알림을 발행한다")
    void compensate_succeedsOnFirstAttempt() {
        // given
        given(orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .willReturn(OrderCompensationResult.compensatingStarted(orderId, payment, userId));
        given(paymentGateway.requestRefund(payment.getPgTransactionId(), payment.getAmount()))
                .willReturn(PgRefundResult.success());
        given(orderCompensationWriter.applyRefundSuccess(orderId, payment.getId()))
                .willReturn(OrderCompensationResult.refunded(orderId, null));

        // when
        orderCompensationService.compensate(orderId, "좌석 매진");

        // then
        verify(paymentGateway, times(1)).requestRefund(payment.getPgTransactionId(), payment.getAmount());
        verify(orderCompensationWriter).applyRefundSuccess(orderId, payment.getId());
        verify(orderEventProducer).publishOrderCancelledNotification(orderId, userId);
        verify(orderCompensationWriter, never()).applyRefundFailure(any());
    }

    @Test
    @DisplayName("처음엔 실패하고 재시도에서 성공하면 그 시점까지만 호출하고 REFUNDED 처리한다")
    void compensate_succeedsAfterRetry() {
        // given
        given(orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .willReturn(OrderCompensationResult.compensatingStarted(orderId, payment, userId));
        given(paymentGateway.requestRefund(payment.getPgTransactionId(), payment.getAmount()))
                .willReturn(PgRefundResult.failure("PG 일시 오류"))
                .willReturn(PgRefundResult.success());
        given(orderCompensationWriter.applyRefundSuccess(orderId, payment.getId()))
                .willReturn(OrderCompensationResult.refunded(orderId, null));

        // when
        orderCompensationService.compensate(orderId, "좌석 매진");

        // then
        verify(paymentGateway, times(2)).requestRefund(payment.getPgTransactionId(), payment.getAmount());
        verify(orderCompensationWriter).applyRefundSuccess(orderId, payment.getId());
        verify(orderEventProducer).publishOrderCancelledNotification(orderId, userId);
    }

    @Test
    @DisplayName("최대 재시도까지 전부 실패하면 FAILED로 전이하고 알림은 발행하지 않는다")
    void compensate_allAttemptsFail_marksFailed() {
        // given
        given(orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .willReturn(OrderCompensationResult.compensatingStarted(orderId, payment, userId));
        given(paymentGateway.requestRefund(payment.getPgTransactionId(), payment.getAmount()))
                .willReturn(PgRefundResult.failure("PG 일시 오류"));

        // when
        orderCompensationService.compensate(orderId, "좌석 매진");

        // then
        verify(paymentGateway, times(3)).requestRefund(payment.getPgTransactionId(), payment.getAmount()); // maxAttempts=3
        verify(orderCompensationWriter).applyRefundFailure(orderId);
        verify(orderCompensationWriter, never()).applyRefundSuccess(any(), any());
        verify(orderEventProducer, never()).publishOrderCancelledNotification(any(), any());
    }
}
