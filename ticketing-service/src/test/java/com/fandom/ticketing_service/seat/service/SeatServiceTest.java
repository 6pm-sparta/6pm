package com.fandom.ticketing_service.seat.service;

import com.fandom.common.exception.CustomException;
import com.fandom.ticketing_service.common.exception.TicketingErrorCode;
import com.fandom.ticketing_service.order.client.OrderClient;
import com.fandom.ticketing_service.order.dto.CreateOrderRequest;
import com.fandom.ticketing_service.order.dto.CreateOrderResponse;
import com.fandom.ticketing_service.seat.domain.entity.ShowSeat;
import com.fandom.ticketing_service.seat.domain.repository.ShowSeatRepository;
import com.fandom.ticketing_service.seat.dto.HoldResponse;
import com.fandom.ticketing_service.seat.dto.ShowSeatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeatService 단위 테스트")
class SeatServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ShowSeatRepository showSeatRepository;

    @Mock
    private OrderClient orderClient;

    @InjectMocks
    private SeatService seatService;

    @Nested
    @DisplayName("좌석 목록 조회")
    class GetSeats {

        @Test
        @DisplayName("Redis에 상태가 없으면 AVAILABLE로 반환한다")
        void getSeats_noRedisStatus_returnsAvailable() {
            // given
            Long showId = 1L;
            ShowSeat seat = ShowSeat.builder().showId(showId).seatName("A-1").grade("VIP").price(100000).build();
            given(showSeatRepository.findAllByShowId(showId)).willReturn(List.of(seat));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.multiGet(anyList())).willReturn(java.util.Arrays.asList((String) null));

            // when
            List<ShowSeatResponse> result = seatService.getSeats(showId);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo("AVAILABLE");
            assertThat(result.get(0).seatName()).isEqualTo("A-1");
        }

        @Test
        @DisplayName("Redis에 HOLDING 상태가 있으면 그대로 반환한다")
        void getSeats_holdingStatus_returnsHolding() {
            // given
            Long showId = 1L;
            ShowSeat seat = ShowSeat.builder().showId(showId).seatName("B-2").grade("R").price(80000).build();
            given(showSeatRepository.findAllByShowId(showId)).willReturn(List.of(seat));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.multiGet(anyList())).willReturn(List.of("HOLDING"));

            // when
            List<ShowSeatResponse> result = seatService.getSeats(showId);

            // then
            assertThat(result.get(0).status()).isEqualTo("HOLDING");
        }

        @Test
        @DisplayName("공연에 좌석이 없으면 빈 목록을 반환한다")
        void getSeats_noSeats_returnsEmpty() {
            // given
            given(showSeatRepository.findAllByShowId(anyLong())).willReturn(List.of());

            // when
            List<ShowSeatResponse> result = seatService.getSeats(99L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("좌석 선점")
    class Hold {

        @Test
        @DisplayName("선점 성공 시 주문이 생성되고 orderId가 반환된다")
        void hold_success() {
            // given
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(1L);
            given(orderClient.create(any(CreateOrderRequest.class))).willReturn(new CreateOrderResponse(orderId));

            // when
            HoldResponse result = seatService.hold(seatId, userId);

            // then
            assertThat(result.orderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("존재하지 않는 좌석이면 SEAT_NOT_FOUND 예외가 발생한다")
        void hold_seatNotFound() {
            // given
            given(showSeatRepository.findById(any())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seatService.hold(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 선점된 좌석이면 SEAT_ALREADY_HELD 예외가 발생한다")
        void hold_alreadyHeld() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(0L);

            // when & then
            assertThatThrownBy(() -> seatService.hold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_ALREADY_HELD);
        }

        @Test
        @DisplayName("잔여 재고가 없으면 NO_INVENTORY 예외가 발생한다")
        void hold_noInventory() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(-1L);

            // when & then
            assertThatThrownBy(() -> seatService.hold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.NO_INVENTORY);
        }

        @Test
        @DisplayName("구매 한도 초과 시 PURCHASE_LIMIT_EXCEEDED 예외가 발생한다")
        void hold_purchaseLimitExceeded() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(-2L);

            // when & then
            assertThatThrownBy(() -> seatService.hold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("주문 생성 실패 시 Redis 선점이 롤백되고 ORDER_CREATE_FAILED 예외가 발생한다")
        void hold_orderCreateFailed_rollbacksRedis() {
            // given
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(1L);
            given(orderClient.create(any())).willThrow(new RuntimeException("order-service 연결 실패"));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when & then
            assertThatThrownBy(() -> seatService.hold(seatId, userId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.ORDER_CREATE_FAILED);

            verify(redisTemplate, times(2)).delete(anyString());
            verify(valueOperations).increment(anyString());
            verify(valueOperations).decrement(anyString());
        }
    }

    @Nested
    @DisplayName("좌석 선점 해제")
    class ReleaseHold {

        @Test
        @DisplayName("선점 해제 성공 시 재고가 복구되고 DB orderId가 해제된다")
        void releaseHold_success() {
            // given
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();
            seat.assignOrder(UUID.randomUUID());

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(1L);

            // when
            seatService.releaseHold(seatId, userId);

            // then
            assertThat(seat.getOrderId()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 좌석이면 SEAT_NOT_FOUND 예외가 발생한다")
        void releaseHold_seatNotFound() {
            // given
            given(showSeatRepository.findById(any())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> seatService.releaseHold(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_NOT_FOUND);
        }

        @Test
        @DisplayName("선점되어 있지 않으면 SEAT_NOT_HELD 예외가 발생한다")
        void releaseHold_notHeld() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(-1L);

            // when & then
            assertThatThrownBy(() -> seatService.releaseHold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_NOT_HELD);
        }

        @Test
        @DisplayName("본인이 선점한 좌석이 아니면 SEAT_HOLD_FORBIDDEN 예외가 발생한다")
        void releaseHold_forbidden() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(-2L);

            // when & then
            assertThatThrownBy(() -> seatService.releaseHold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_HOLD_FORBIDDEN);
        }

        @Test
        @DisplayName("주문 생성이 아직 진행 중이면 SEAT_HOLD_PROCESSING 예외가 발생한다")
        void releaseHold_processing() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(1L).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(-3L);

            // when & then
            assertThatThrownBy(() -> seatService.releaseHold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_HOLD_PROCESSING);
        }
    }
}
