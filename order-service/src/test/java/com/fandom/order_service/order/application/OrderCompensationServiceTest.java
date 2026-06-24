package com.fandom.order_service.order.application;

import com.fandom.order_service.order.application.compensation.OrderCompensationResult;
import com.fandom.order_service.order.application.compensation.OrderCompensationService;
import com.fandom.order_service.order.application.compensation.OrderCompensationWriter;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompensationService 단위 테스트")
class OrderCompensationServiceTest {

    @Mock
    private OrderCompensationWriter orderCompensationWriter;

    @Mock
    private PaymentGateway paymentGateway;

    private OrderCompensationService orderCompensationService;

    private UUID orderId;
    private UUID userId;
    private Payment payment;

    @BeforeEach
    void setUp() {
        orderCompensationService = new OrderCompensationService(orderCompensationWriter, paymentGateway);

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
        verify(paymentGateway, never()).requestRefundAsync(any(), any(), any());
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
        verify(paymentGateway, never()).requestRefundAsync(any(), any(), any());
    }

    @Test
    @DisplayName("REFUND_REQUESTED_STARTED면 비동기 환불 요청을 한 번 접수시키고 끝낸다(결과는 webhook으로 비동기 반영)")
    void compensate_started_requestsRefundAsyncOnce() {
        // given
        given(orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .willReturn(OrderCompensationResult.refundRequestedStarted(orderId, payment, userId));

        // when
        orderCompensationService.compensate(orderId, "좌석 매진");

        // then
        verify(paymentGateway).requestRefundAsync(orderId, payment.getPgTransactionId(), payment.getAmount());
    }

    @Test
    @DisplayName("PG 접수 자체가 실패해도 예외를 다시 던지지 않는다(Kafka 무한 재전송 방지, 로그만 남김)")
    void compensate_pgAcceptFails_doesNotPropagateException() {
        // given
        given(orderCompensationWriter.startCompensation(orderId, "좌석 매진"))
                .willReturn(OrderCompensationResult.refundRequestedStarted(orderId, payment, userId));
        willThrow(new RuntimeException("connection refused"))
                .given(paymentGateway).requestRefundAsync(orderId, payment.getPgTransactionId(), payment.getAmount());

        // when & then
        assertThatCode(() -> orderCompensationService.compensate(orderId, "좌석 매진"))
                .doesNotThrowAnyException();
    }
}