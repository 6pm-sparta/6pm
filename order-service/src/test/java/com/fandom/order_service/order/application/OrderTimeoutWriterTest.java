package com.fandom.order_service.order.application;

import com.fandom.order_service.order.application.timeout.OrderTimeoutResult;
import com.fandom.order_service.order.application.timeout.OrderTimeoutWriter;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.entity.OrderStatusHistory;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTimeoutWriter 단위 테스트")
class OrderTimeoutWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    private OrderTimeoutWriter orderTimeoutWriter;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderTimeoutWriter = new OrderTimeoutWriter(orderRepository, orderStatusHistoryRepository);
        orderId = UUID.randomUUID();
    }

    private Order pendingOrderWithId() {
        Order order = Order.createPending(UUID.randomUUID(), UUID.randomUUID(), 50_000L,
                LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    @Test
    @DisplayName("PENDING 상태면 CANCELLED로 전이하고 history를 남긴다")
    void expireIfStillPending_pending_cancelsAndRecordsHistory() {
        // given
        Order order = pendingOrderWithId();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

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
        assertThat(history.getReason()).isEqualTo("주문 타임아웃 자동 취소");
    }

    @Test
    @DisplayName("이미 다른 상태(PAYMENT_REQUESTED)로 바뀐 주문은 SKIPPED를 반환하고 변경하지 않는다 — " +
            "조회와 처리 사이에 결제 요청이 먼저 들어간 경합 상황")
    void expireIfStillPending_alreadyPaymentRequested_skips() {
        // given
        Order order = pendingOrderWithId();
        order.markPaymentRequested();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderTimeoutResult result = orderTimeoutWriter.expireIfStillPending(orderId);

        // then
        assertThat(result).isEqualTo(OrderTimeoutResult.SKIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_REQUESTED);
        verify(orderStatusHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
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
    }
}
