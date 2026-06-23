package com.fandom.order_service.kafka.consumer;

import com.fandom.order_service.kafka.event.SeatBookFailedEvent;
import com.fandom.order_service.kafka.event.SeatBookedEvent;
import com.fandom.order_service.order.application.compensation.OrderCompensationService;
import com.fandom.order_service.order.application.confirmation.OrderConfirmationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeatEventConsumer 단위 테스트")
class SeatEventConsumerTest {

    @Mock
    private OrderConfirmationService orderConfirmationService;

    @Mock
    private OrderCompensationService orderCompensationService;

    private SeatEventConsumer seatEventConsumer;

    @BeforeEach
    void setUp() {
        seatEventConsumer = new SeatEventConsumer(orderConfirmationService, orderCompensationService);
    }

    @Test
    @DisplayName("ticketing.seat.booked 수신 시 OrderConfirmationService.confirmOrder를 orderId로 호출한다")
    void onSeatBooked_delegatesToConfirmationService() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        SeatBookedEvent event = new SeatBookedEvent(orderId, seatId);

        // when
        seatEventConsumer.onSeatBooked(event);

        // then
        verify(orderConfirmationService).confirmOrder(orderId);
    }

    @Test
    @DisplayName("ticketing.seat.book.failed 수신 시 OrderCompensationService.compensate를 orderId/reason으로 호출한다")
    void onSeatBookFailed_delegatesToCompensationService() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        SeatBookFailedEvent event = new SeatBookFailedEvent(orderId, seatId, "좌석 매진");

        // when
        seatEventConsumer.onSeatBookFailed(event);

        // then
        verify(orderCompensationService).compensate(orderId, "좌석 매진");
    }
}