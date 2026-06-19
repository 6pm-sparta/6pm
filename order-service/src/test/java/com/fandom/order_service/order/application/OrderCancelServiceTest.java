package com.fandom.order_service.order.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.presentation.dto.response.OrderCancelResponse;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
import com.fandom.order_service.payment.infra.pg.PgRefundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelService 단위 테스트")
class OrderCancelServiceTest {

    @Mock
    private OrderCancelWriter orderCancelWriter;

    @Mock
    private PaymentGateway paymentGateway;

    private OrderCancelService orderCancelService;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderCancelService = new OrderCancelService(orderCancelWriter, paymentGateway);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    private Payment approvedPayment() {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CARD)
                .pgTransactionId("PG-1234")
                .idempotencyKey("idem-key-cancel")
                .build();
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    @Test
    @DisplayName("PENDING 취소(CANCELLED)는 PG 호출 없이 바로 응답한다")
    void cancelOrder_cancelled_noPgCall() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(orderCancelWriter.decide(orderId, userId))
                .willReturn(OrderCancelDecision.cancelled(orderId, OrderStatus.CANCELLED, now));

        // when
        OrderCancelResponse response = orderCancelService.cancelOrder(orderId, userId);

        // then
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.paymentId()).isNull();
        verify(paymentGateway, never()).requestRefund(any(), any());
    }

    @Test
    @DisplayName("멱등 응답(IDEMPOTENT)도 PG 호출 없이 현재 상태를 그대로 반환한다")
    void cancelOrder_idempotent_noPgCall() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(orderCancelWriter.decide(orderId, userId))
                .willReturn(OrderCancelDecision.idempotent(orderId, OrderStatus.REFUNDED, now));

        // when
        OrderCancelResponse response = orderCancelService.cancelOrder(orderId, userId);

        // then
        assertThat(response.status()).isEqualTo("REFUNDED");
        verify(paymentGateway, never()).requestRefund(any(), any());
    }

    @Test
    @DisplayName("PAID 취소는 PG 환불 호출 성공 시 REFUNDED + paymentId로 응답한다")
    void cancelOrder_refundNeeded_success() {
        // given
        Payment payment = approvedPayment();
        given(orderCancelWriter.decide(orderId, userId))
                .willReturn(OrderCancelDecision.refundNeeded(orderId, payment));
        given(paymentGateway.requestRefund(payment.getPgTransactionId(), payment.getAmount()))
                .willReturn(PgRefundResult.success());

        LocalDateTime refundedAt = LocalDateTime.now();
        given(orderCancelWriter.applyRefundSuccess(orderId, payment.getId()))
                .willReturn(OrderCancelDecision.refunded(orderId, OrderStatus.REFUNDED, payment.getId(), refundedAt));

        // when
        OrderCancelResponse response = orderCancelService.cancelOrder(orderId, userId);

        // then
        assertThat(response.status()).isEqualTo("REFUNDED");
        assertThat(response.paymentId()).isEqualTo(payment.getId());
        verify(paymentGateway).requestRefund(payment.getPgTransactionId(), payment.getAmount());
        verify(orderCancelWriter).applyRefundSuccess(orderId, payment.getId());
    }

    @Test
    @DisplayName("PG 환불 실패 시 PG_ERROR(502) 예외를 던지고, applyRefundSuccess는 호출하지 않는다")
    void cancelOrder_refundNeeded_pgFailure_throwsPgError() {
        // given
        Payment payment = approvedPayment();
        given(orderCancelWriter.decide(orderId, userId))
                .willReturn(OrderCancelDecision.refundNeeded(orderId, payment));
        given(paymentGateway.requestRefund(payment.getPgTransactionId(), payment.getAmount()))
                .willReturn(PgRefundResult.failure("PG 사 내부 오류"));

        // when & then
        assertThatThrownBy(() -> orderCancelService.cancelOrder(orderId, userId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PG_ERROR);

        verify(orderCancelWriter, never()).applyRefundSuccess(any(), any());
    }
}
