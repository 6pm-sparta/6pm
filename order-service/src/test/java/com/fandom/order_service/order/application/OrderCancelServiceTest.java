package com.fandom.order_service.order.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.application.cancellation.OrderCancelDecision;
import com.fandom.order_service.order.application.cancellation.OrderCancelService;
import com.fandom.order_service.order.application.cancellation.OrderCancelWriter;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.presentation.dto.response.OrderCancelResponse;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.infra.pg.PaymentGateway;
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
import static org.mockito.BDDMockito.willThrow;
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
        verify(paymentGateway, never()).requestRefundAsync(any(), any(), any());
    }

    @Test
    @DisplayName("멱등 응답(IDEMPOTENT)도 PG 호출 없이 현재 상태를 그대로 반환한다")
    void cancelOrder_idempotent_noPgCall() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(orderCancelWriter.decide(orderId, userId))
                .willReturn(OrderCancelDecision.idempotent(orderId, OrderStatus.CANCELLED, now));

        // when
        OrderCancelResponse response = orderCancelService.cancelOrder(orderId, userId);

        // then
        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(paymentGateway, never()).requestRefundAsync(any(), any(), any());
    }

    @Test
    @DisplayName("CONFIRMING 취소는 비동기 환불을 접수만 시키고 CANCEL_REQUESTED + paymentId로 즉시 응답한다")
    void cancelOrder_refundNeeded_acceptsAsyncRefundImmediately() {
        // given
        Payment payment = approvedPayment();
        LocalDateTime refundRequestedAt = LocalDateTime.now();
        given(orderCancelWriter.decide(orderId, userId))
                .willReturn(OrderCancelDecision.refundNeeded(orderId, payment, refundRequestedAt));

        // when
        OrderCancelResponse response = orderCancelService.cancelOrder(orderId, userId);

        // then — 환불 완료(CANCELLED)를 기다리지 않고 CANCEL_REQUESTED로 즉시 응답한다
        assertThat(response.status()).isEqualTo("CANCEL_REQUESTED");
        assertThat(response.paymentId()).isEqualTo(payment.getId());
        assertThat(response.updatedAt()).isEqualTo(refundRequestedAt);
        verify(paymentGateway).requestRefundAsync(orderId, payment.getPgTransactionId(), payment.getAmount());
    }

    @Test
    @DisplayName("PG 환불 접수 자체가 실패하면 PG_ERROR(502) 예외를 던진다(주문은 CANCEL_REQUESTED에 머문다)")
    void cancelOrder_refundNeeded_pgAcceptFails_throwsPgError() {
        // given
        Payment payment = approvedPayment();
        given(orderCancelWriter.decide(orderId, userId))
                .willReturn(OrderCancelDecision.refundNeeded(orderId, payment, LocalDateTime.now()));
        willThrow(new RuntimeException("connection refused"))
                .given(paymentGateway).requestRefundAsync(orderId, payment.getPgTransactionId(), payment.getAmount());

        // when & then
        assertThatThrownBy(() -> orderCancelService.cancelOrder(orderId, userId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PG_ERROR);
    }
}
