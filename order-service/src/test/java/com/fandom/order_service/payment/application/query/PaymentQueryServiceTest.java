package com.fandom.order_service.payment.application.query;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentMethod;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.presentation.dto.response.PaymentDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentQueryService 단위 테스트")
class PaymentQueryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    private PaymentQueryService paymentQueryService;

    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        paymentQueryService = new PaymentQueryService(paymentRepository, orderRepository);
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    private Order orderOwnedBy(UUID userId) {
        Order order = Order.createPending(UUID.randomUUID(), userId, 50_000L, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    private Payment paymentOf(UUID orderId) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(50_000L)
                .paymentStatus(PaymentStatus.APPROVED)
                .paymentMethod(PaymentMethod.CARD)
                .pgTransactionId("PG-1234")
                .idempotencyKey(UUID.randomUUID().toString())
                .build();
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }

    @Test
    @DisplayName("본인 주문의 결제면 단건 조회에 성공한다")
    void getPayment_success() {
        // given
        Order order = orderOwnedBy(ownerId);
        Payment payment = paymentOf(order.getId());
        given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        // when
        PaymentDetailResponse response = paymentQueryService.getPayment(payment.getId(), ownerId);

        // then
        assertThat(response.paymentId()).isEqualTo(payment.getId());
        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.pgTransactionId()).isEqualTo("PG-1234");
        assertThat(response.refundAmount()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 결제면 PAYMENT_NOT_FOUND(404)를 던진다")
    void getPayment_notFound() {
        // given
        UUID paymentId = UUID.randomUUID();
        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentQueryService.getPayment(paymentId, ownerId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 주문의 결제가 아니면 PAYMENT_ACCESS_DENIED(403)를 던진다")
    void getPayment_forbidden() {
        // given
        Order order = orderOwnedBy(ownerId);
        Payment payment = paymentOf(order.getId());
        given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentQueryService.getPayment(payment.getId(), otherUserId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("본인 주문이면 결제 시도 목록을 최신순 그대로 반환한다")
    void getPaymentsByOrder_success() {
        // given
        Order order = orderOwnedBy(ownerId);
        Payment latest = paymentOf(order.getId());
        Payment older = paymentOf(order.getId());
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId()))
                .willReturn(List.of(latest, older));

        // when
        List<PaymentDetailResponse> response = paymentQueryService.getPaymentsByOrder(order.getId(), ownerId);

        // then
        assertThat(response).hasSize(2);
        assertThat(response.get(0).paymentId()).isEqualTo(latest.getId());
        assertThat(response.get(1).paymentId()).isEqualTo(older.getId());
    }

    @Test
    @DisplayName("결제 시도가 0건이면 빈 배열을 반환한다 (404 아님)")
    void getPaymentsByOrder_emptyResult() {
        // given
        Order order = orderOwnedBy(ownerId);
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId()))
                .willReturn(List.of());

        // when
        List<PaymentDetailResponse> response = paymentQueryService.getPaymentsByOrder(order.getId(), ownerId);

        // then
        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND(404)를 던진다")
    void getPaymentsByOrder_orderNotFound() {
        // given
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentQueryService.getPaymentsByOrder(orderId, ownerId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 주문이 아니면 ORDER_ACCESS_DENIED(403)를 던진다")
    void getPaymentsByOrder_forbidden() {
        // given
        Order order = orderOwnedBy(ownerId);
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentQueryService.getPaymentsByOrder(order.getId(), otherUserId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_ACCESS_DENIED);
    }
}
