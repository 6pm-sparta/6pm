package com.fandom.ticketing_service.seat.application;

import com.fandom.common.exception.CustomException;
import com.fandom.ticketing_service.common.exception.TicketingErrorCode;
import com.fandom.ticketing_service.kafka.event.SeatBookFailedEvent;
import com.fandom.ticketing_service.kafka.event.SeatBookedEvent;
import com.fandom.ticketing_service.kafka.producer.SeatEventProducer;
import com.fandom.ticketing_service.seat.domain.entity.ShowSeat;
import com.fandom.ticketing_service.seat.domain.repository.ShowSeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeatConfirmService 단위 테스트")
class SeatConfirmServiceTest {

    @Mock
    private ShowSeatRepository showSeatRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SeatEventProducer seatEventProducer;

    @InjectMocks
    private SeatConfirmService seatConfirmService;

    @Nested
    @DisplayName("좌석 확정 (confirmSeat)")
    class ConfirmSeat {

        @Test
        @DisplayName("결제 성공 시 좌석이 BOOKED로 전환되고 seat.booked 이벤트가 발행된다")
        void confirmSeat_success() {
            // given
            UUID orderId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();
            seat.assignOrder(orderId);

            given(showSeatRepository.findByOrderId(orderId)).willReturn(Optional.of(seat));

            // when
            seatConfirmService.confirmSeat(orderId);

            // then
            verify(redisTemplate, times(2)).delete(anyString());

            ArgumentCaptor<SeatBookedEvent> captor = ArgumentCaptor.forClass(SeatBookedEvent.class);
            verify(seatEventProducer).publishSeatBooked(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("좌석을 찾지 못하면 CustomException을 던지고 seatId=null인 실패 이벤트를 발행한다")
        void confirmSeat_seatNotFound_throwsAndPublishesFailedWithNullSeatId() {
            // given
            UUID orderId = UUID.randomUUID();
            given(showSeatRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seatConfirmService.confirmSeat(orderId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_NOT_FOUND);

            ArgumentCaptor<SeatBookFailedEvent> captor = ArgumentCaptor.forClass(SeatBookFailedEvent.class);
            verify(seatEventProducer).publishSeatBookFailed(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(orderId);
            assertThat(captor.getValue().seatId()).isNull();
            verify(seatEventProducer, never()).publishSeatBooked(any());
        }

        @Test
        @DisplayName("좌석 확정 처리 중 예외가 발생하면 seatId가 채워진 seat.book.failed 이벤트가 발행된다")
        void confirmSeat_failureAfterSeatFound_publishesFailedWithSeatId() {
            // given
            UUID orderId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();
            seat.assignOrder(orderId);

            given(showSeatRepository.findByOrderId(orderId)).willReturn(Optional.of(seat));
            doThrow(new RuntimeException("redis down")).when(redisTemplate).delete(anyString());

            // when
            seatConfirmService.confirmSeat(orderId);

            // then
            ArgumentCaptor<SeatBookFailedEvent> captor = ArgumentCaptor.forClass(SeatBookFailedEvent.class);
            verify(seatEventProducer).publishSeatBookFailed(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(orderId);
            assertThat(captor.getValue().seatId()).isEqualTo(seat.getId());
        }
    }

    @Nested
    @DisplayName("좌석 해제 (releaseSeat)")
    class ReleaseSeat {

        @Test
        @DisplayName("결제 실패/취소 시 좌석이 AVAILABLE로 복원되고 Redis 재고가 복구된다")
        void releaseSeat_success() {
            // given
            UUID orderId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();
            seat.assignOrder(orderId);

            given(showSeatRepository.findByOrderId(orderId)).willReturn(Optional.of(seat));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when
            seatConfirmService.releaseSeat(orderId);

            // then
            assertThat(seat.getOrderId()).isNull();
            verify(redisTemplate, times(2)).delete(anyString());
            verify(valueOperations).increment(anyString());
        }

        @Test
        @DisplayName("orderId에 해당하는 좌석이 없으면 아무 작업도 하지 않는다")
        void releaseSeat_notFound_noOp() {
            // given
            UUID orderId = UUID.randomUUID();
            given(showSeatRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            // when
            seatConfirmService.releaseSeat(orderId);

            // then
            verify(redisTemplate, never()).delete(anyString());
            verify(seatEventProducer, never()).publishSeatBooked(any());
        }
    }
}
