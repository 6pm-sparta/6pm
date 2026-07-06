package com.fandom.order_service.order.application;

import com.fandom.order_service.kafka.outbox.application.OutboxAppender;
import com.fandom.order_service.order.application.timeout.OrderTimeoutResult;
import com.fandom.order_service.order.application.timeout.OrderTimeoutWriter;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * issue #292 — PAYMENT_REQUESTED가 PENDING에 흡수되면서, 진행중 결제(payments.REQUESTED)가 있으면
 * 타임아웃 취소를 스킵하는 가드가 새로 생겼다. PaymentRepository 의존성 추가에 따른 테스트 반영.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTimeoutWriter 단위 테스트")
class OrderTimeoutWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxAppender outboxAppender;

    private OrderTimeoutWriter orderTimeoutWriter;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderTimeoutWriter = new OrderTimeoutWriter(orderRepository, orderStatusHistoryRepository, paymentRepository, outboxAppender);
        orderId = UUID.randomUUID();
    }

    private Order pendingOrderWithId() {
        Order order = Order.createPending(UUID.randomUUID(), UUID.randomUUID(), 50_000L,
                LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    @Test
    @DisplayName("PENDING이고 진행중 결제가 없으면 CANCELLED로 전이하고 history를 남긴다")
    void expireIfStillPending_pendingNoInFlightPayment_cancelsAndRecordsHistory() {
        // given
        Order order = pendingOrderWithId();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED)).willReturn(false);

        // when
        OrderTimeoutResult result = orderTimeoutWriter.expireIfStillPending(orderId);

        // then
        assertThat(result).isEqualTo(OrderTimeoutResult.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());
        OrderStatusHistory history = historyCaptor.getValue();
        assertThat(history.getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(history.getReason()).isEqualTo("[USER] 주문 타임아웃 자동 취소");
        verify(outboxAppender).appendHoldReleased(order.getId());
    }

    @Test
    @DisplayName("issue #292 — PENDING이어도 진행중(REQUESTED) 결제가 있으면 SKIPPED를 반환하고 변경하지 않는다 — " +
            "웹훅 결과를 기다려야 하는 상황(오취소 방지)")
    void expireIfStillPending_pendingWithInFlightPayment_skips() {
        // given
        Order order = pendingOrderWithId();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
        given(paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.REQUESTED)).willReturn(true);

        // when
        OrderTimeoutResult result = orderTimeoutWriter.expireIfStillPending(orderId);

        // then
        assertThat(result).isEqualTo(OrderTimeoutResult.SKIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING); // 변경 없음
        verify(orderStatusHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(outboxAppender, never()).appendHoldReleased(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이미 다른 상태(CANCELLED)로 바뀐 주문은 SKIPPED를 반환한다 — " +
            "조회와 처리 사이에 유저가 먼저 직접 취소한 경합 상황")
    void expireIfStillPending_alreadyCancelled_skips() {
        // given
        Order order = pendingOrderWithId();
        order.markCancelled();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderTimeoutResult result = orderTimeoutWriter.expireIfStillPending(orderId);

        // then
        assertThat(result).isEqualTo(OrderTimeoutResult.SKIPPED);
        verify(orderStatusHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(outboxAppender, never()).appendHoldReleased(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("주문이 존재하지 않으면 예외를 던지지 않고 SKIPPED를 반환한다 — " +
            "배치 처리이므로 한 건의 데이터 정합성 문제가 전체를 막으면 안 됨")
    void expireIfStillPending_orderNotFound_skipsWithoutThrowing() {
        // given
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.empty());

        // when
        OrderTimeoutResult result = orderTimeoutWriter.expireIfStillPending(orderId);

        // then
        assertThat(result).isEqualTo(OrderTimeoutResult.SKIPPED);
        verify(orderStatusHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(outboxAppender, never()).appendHoldReleased(org.mockito.ArgumentMatchers.any());
    }
}
