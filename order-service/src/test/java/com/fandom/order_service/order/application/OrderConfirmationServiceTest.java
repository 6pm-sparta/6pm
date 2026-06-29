package com.fandom.order_service.order.application;

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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 이벤트 발행은 Writer 트랜잭션 안에서 Outbox로 처리되므로(OrderConfirmationWriterTest에서 검증),
 * 여기서는 Service가 Writer에 위임하고 결과 타입별로 예외 없이 처리하는지만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConfirmationService 단위 테스트")
class OrderConfirmationServiceTest {

    @Mock
    private OrderConfirmationWriter orderConfirmationWriter;

    private OrderConfirmationService orderConfirmationService;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderConfirmationService = new OrderConfirmationService(orderConfirmationWriter);
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Writer.confirm에 위임한다")
    void confirmOrder_delegatesToWriter() {
        // given
        given(orderConfirmationWriter.confirm(orderId))
                .willReturn(OrderConfirmationResult.confirmed(orderId, userId));

        // when
        orderConfirmationService.confirmOrder(orderId);

        // then
        verify(orderConfirmationWriter).confirm(orderId);
    }

    @Test
    @DisplayName("ALREADY_CONFIRMED / SKIPPED_INVALID_STATE 결과도 예외 없이 처리한다")
    void confirmOrder_nonConfirmedResults_doNotThrow() {
        // given
        given(orderConfirmationWriter.confirm(orderId))
                .willReturn(OrderConfirmationResult.alreadyConfirmed(orderId));

        // when & then
        assertThatCode(() -> orderConfirmationService.confirmOrder(orderId)).doesNotThrowAnyException();
    }
}
