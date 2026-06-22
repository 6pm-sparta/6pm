package com.fandom.order_service.order.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.application.confirmation.OrderConfirmationResult;
import com.fandom.order_service.order.application.confirmation.OrderConfirmationWriter;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.domain.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConfirmationWriter 단위 테스트")
class OrderConfirmationWriterTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    private OrderConfirmationWriter orderConfirmationWriter;

    private UUID orderId;
    private UUID userId;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        orderConfirmationWriter = new OrderConfirmationWriter(orderRepository, orderStatusHistoryRepository);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        seatId = UUID.randomUUID();
    }

    private Order paidOrder() {
        Order order = Order.createPending(seatId, userId, 50_000L, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", orderId);
        order.markPaymentRequested();
        order.markPaid();
        return order;
    }

    @Test
    @DisplayName("PAID 주문은 CONFIRMED로 전이되고 이력이 남는다")
    void confirm_paid_transitionsToConfirmed() {
        // given
        Order order = paidOrder();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderConfirmationResult result = orderConfirmationWriter.confirm(orderId);

        // then
        assertThat(result.type()).isEqualTo(OrderConfirmationResult.Type.CONFIRMED);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    @DisplayName("이미 CONFIRMED인 주문은 변경 없이 ALREADY_CONFIRMED로 응답한다(중복 이벤트 멱등 처리)")
    void confirm_alreadyConfirmed_returnsIdempotent() {
        // given
        Order order = paidOrder();
        order.markConfirmed();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderConfirmationResult result = orderConfirmationWriter.confirm(orderId);

        // then
        assertThat(result.type()).isEqualTo(OrderConfirmationResult.Type.ALREADY_CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("PAID가 아닌 다른 상태(예: REFUND_REQUESTED)면 SKIPPED_INVALID_STATE로 응답하고 예외를 던지지 않는다")
    void confirm_notPaid_returnsSkipped() {
        // given
        Order order = paidOrder();
        order.markRefundRequested();
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));

        // when
        OrderConfirmationResult result = orderConfirmationWriter.confirm(orderId);

        // then
        assertThat(result.type()).isEqualTo(OrderConfirmationResult.Type.SKIPPED_INVALID_STATE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED); // 변경 없음
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 예외가 발생한다")
    void confirm_orderNotFound_throws() {
        // given
        given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderConfirmationWriter.confirm(orderId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }
}
