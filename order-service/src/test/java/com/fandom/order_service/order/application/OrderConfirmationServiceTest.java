package com.fandom.order_service.order.application;

import com.fandom.order_service.kafka.producer.OrderEventProducer;
import com.fandom.order_service.order.application.confirmation.OrderConfirmationResult;
import com.fandom.order_service.order.application.confirmation.OrderConfirmationService;
import com.fandom.order_service.order.application.confirmation.OrderConfirmationWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConfirmationService 단위 테스트")
class OrderConfirmationServiceTest {

    @Mock
    private OrderConfirmationWriter orderConfirmationWriter;

    @Mock
    private OrderEventProducer orderEventProducer;

    private OrderConfirmationService orderConfirmationService;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderConfirmationService = new OrderConfirmationService(orderConfirmationWriter, orderEventProducer);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("CONFIRMED 전이 성공 시 notification.send(ORDER_COMPLETED)를 발행한다")
    void confirmOrder_confirmed_publishesEvent() {
        // given
        given(orderConfirmationWriter.confirm(orderId))
                .willReturn(OrderConfirmationResult.confirmed(orderId, userId));

        // when
        orderConfirmationService.confirmOrder(orderId);

        // then
        verify(orderEventProducer).publishOrderCompletedNotification(orderId, userId);
    }

    @Test
    @DisplayName("이미 CONFIRMED(중복 이벤트)면 이벤트를 다시 발행하지 않는다")
    void confirmOrder_alreadyConfirmed_doesNotPublish() {
        // given
        given(orderConfirmationWriter.confirm(orderId))
                .willReturn(OrderConfirmationResult.alreadyConfirmed(orderId));

        // when
        orderConfirmationService.confirmOrder(orderId);

        // then
        verify(orderEventProducer, never()).publishOrderCompletedNotification(any(), any());
    }

    @Test
    @DisplayName("CONFIRMED로 전이할 수 없는 상태면 이벤트를 발행하지 않는다")
    void confirmOrder_skippedInvalidState_doesNotPublish() {
        // given
        given(orderConfirmationWriter.confirm(orderId))
                .willReturn(OrderConfirmationResult.skippedInvalidState(orderId));

        // when
        orderConfirmationService.confirmOrder(orderId);

        // then
        verify(orderEventProducer, never()).publishOrderCompletedNotification(any(), any());
    }
}
