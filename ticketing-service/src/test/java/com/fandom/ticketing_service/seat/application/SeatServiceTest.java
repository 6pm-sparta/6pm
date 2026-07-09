package com.fandom.ticketing_service.seat.application;

import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import com.fandom.ticketing_service.common.exception.TicketingErrorCode;
import com.fandom.ticketing_service.order.infrastructure.client.OrderClient;
import com.fandom.ticketing_service.order.infrastructure.dto.CreateOrderRequest;
import com.fandom.ticketing_service.order.infrastructure.dto.CreateOrderResponse;
import com.fandom.ticketing_service.queue.application.PurchaseTokenService;
import com.fandom.ticketing_service.seat.domain.entity.ShowSeat;
import com.fandom.ticketing_service.seat.domain.repository.ShowSeatRepository;
import com.fandom.ticketing_service.seat.presentation.dto.HoldResponse;
import com.fandom.ticketing_service.seat.presentation.dto.PurchaseLimitResponse;
import com.fandom.ticketing_service.seat.presentation.dto.ShowSeatResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
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

    @Mock
    private PurchaseTokenService purchaseTokenService;

    // real MeterRegistry는 실제 환경(Spring context)에서만 만들어짐 → 단위 테스트에선 격리를 위해 mock으로 대체.
    // deep stub: meterRegistry.counter(...)가 null이 아니라 mock Counter를 반환하게 해서 increment() NPE를 막는다.
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks
    private SeatService seatService;

    @Nested
    @DisplayName("좌석 목록 조회")
    class GetSeats {

        @Test
        @DisplayName("Redis에 상태가 없으면 AVAILABLE로 반환한다")
        void getSeats_noRedisStatus_returnsAvailable() {
            // given
            UUID showId = UUID.randomUUID();
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
            UUID showId = UUID.randomUUID();
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
            given(showSeatRepository.findAllByShowId(any(UUID.class))).willReturn(List.of());

            // when
            List<ShowSeatResponse> result = seatService.getSeats(UUID.randomUUID());

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("좌석 선점")
    class Hold {

        @Test
        @DisplayName("선점 성공 시 주문생성 호출 없이 끝난다")
        void hold_success() {
            // given
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.hasKey(anyString())).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(1L);

            // when & then
            seatService.hold(seatId, userId);

            verifyNoInteractions(orderClient);
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
        @DisplayName("구매 토큰이 없으면 PURCHASE_TOKEN_NOT_FOUND 예외가 발생한다")
        void hold_noPurchaseToken() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> seatService.hold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.PURCHASE_TOKEN_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 선점된 좌석이면 SEAT_ALREADY_HELD 예외가 발생한다")
        void hold_alreadyHeld() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.hasKey(anyString())).willReturn(true);
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
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.hasKey(anyString())).willReturn(true);
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
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.hasKey(anyString())).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(-2L);

            // when & then
            assertThatThrownBy(() -> seatService.hold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }

    }

    @Nested
    @DisplayName("체크아웃(주문 생성)")
    class Checkout {

        @Test
        @DisplayName("HELD 상태에서 체크아웃하면 주문이 생성되고 orderId가 반환된다")
        void checkout_success() {
            // given
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(1L);
            given(orderClient.create(any(CreateOrderRequest.class))).willReturn(ApiResponse.created(new CreateOrderResponse(orderId)));

            // when
            HoldResponse result = seatService.checkout(seatId, userId);

            // then
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(seat.getOrderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("이미 CONFIRMED 상태면 주문생성 없이 기존 orderId로 멱등 응답한다")
        void checkout_alreadyConfirmed_idempotent() {
            // given
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();
            seat.assignOrder(orderId);

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(2L);

            // when
            HoldResponse result = seatService.checkout(seatId, userId);

            // then
            assertThat(result.orderId()).isEqualTo(orderId);
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("본인 선점이 아니면 SEAT_HOLD_FORBIDDEN 예외가 발생한다")
        void checkout_forbidden() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(-2L);

            // when & then
            assertThatThrownBy(() -> seatService.checkout(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_HOLD_FORBIDDEN);
        }

        @Test
        @DisplayName("이미 체크아웃 처리 중이면 SEAT_HOLD_PROCESSING 예외가 발생한다")
        void checkout_processing() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(-3L);

            // when & then
            assertThatThrownBy(() -> seatService.checkout(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_HOLD_PROCESSING);
        }

        @Test
        @DisplayName("선점되어 있지 않으면 SEAT_NOT_HELD 예외가 발생한다")
        void checkout_notHeld() {
            // given
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(-1L);

            // when & then
            assertThatThrownBy(() -> seatService.checkout(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_NOT_HELD);
        }

        @Test
        @DisplayName("주문 생성 실패 시 이 좌석의 선점만 롤백되고 ORDER_CREATE_FAILED 예외가 발생한다")
        void checkout_orderCreateFailed_rollbacksRedis() {
            // given
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(purchaseTokenService.exists(any(UUID.class), any())).willReturn(true);
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(1L);
            given(orderClient.create(any())).willThrow(new RuntimeException("order-service 연결 실패"));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when & then
            assertThatThrownBy(() -> seatService.checkout(seatId, userId))
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
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();
            seat.assignOrder(UUID.randomUUID());

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(1L);

            // when
            seatService.releaseHold(seatId, userId);

            // then
            assertThat(seat.getOrderId()).isNull();
        }

        @Test
        // (2026-07-09, ADR 011) order-service 자체 취소 경로의 self-heal로 충분하다고 결론나서
        // releaseHold()는 order-service를 아예 호출하지 않는다.
        @DisplayName("선점 해제 시 연결된 주문이 있어도 order-service를 호출하지 않는다 (의도적 미연동)")
        void releaseHold_success_doesNotCancelOrder() {
            // given
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();
            seat.assignOrder(orderId);

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(1L);

            // when
            seatService.releaseHold(seatId, userId);

            // then
            verifyNoInteractions(orderClient);
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
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

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
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

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
            ShowSeat seat = ShowSeat.builder().showId(UUID.randomUUID()).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.execute(any(RedisScript.class), anyList(), any())).willReturn(-3L);

            // when & then
            assertThatThrownBy(() -> seatService.releaseHold(seatId, UUID.randomUUID()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(TicketingErrorCode.SEAT_HOLD_PROCESSING);
        }
    }

    @Nested
    @DisplayName("좌석 선점 TTL 만료 해제 (releaseExpiredHold)")
    class ReleaseExpiredHold {

        @Test
        @DisplayName("체크아웃 전(orderId==null) 방치된 hold도 재고와 purchase-count가 복구된다")
        void releaseExpiredHold_neverCheckedOut_restoresInventoryAndPurchaseCount() {
            // given
            UUID showId = UUID.randomUUID();
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(showId).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(userId + ":HELD");

            // when
            seatService.releaseExpiredHold(showId, seatId);

            // then
            verify(valueOperations).increment("inventory:%s".formatted(showId));
            verify(valueOperations).decrement("purchase-count:%s:%s".formatted(userId, showId));
            verify(redisTemplate).delete(anyString());
        }

        @Test
        @DisplayName("주문 생성까지 끝난(CONFIRMED, orderId!=null) hold가 만료되면 DB orderId도 해제된다")
        void releaseExpiredHold_confirmed_releasesOrder() {
            // given
            UUID showId = UUID.randomUUID();
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(showId).seatName("A-1").grade("VIP").price(100000).build();
            seat.assignOrder(UUID.randomUUID());

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(userId + ":CONFIRMED");

            // when
            seatService.releaseExpiredHold(showId, seatId);

            // then
            assertThat(seat.getOrderId()).isNull();
            verify(valueOperations).increment("inventory:%s".formatted(showId));
            verify(valueOperations).decrement("purchase-count:%s:%s".formatted(userId, showId));
        }

        @Test
        @DisplayName("체크아웃 진행 중(PENDING)인 hold의 TTL이 만료되면 해제를 스킵한다 (#325)")
        void releaseExpiredHold_pending_skipsRelease() {
            // given
            UUID showId = UUID.randomUUID();
            UUID seatId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(userId + ":PENDING");

            // when
            seatService.releaseExpiredHold(showId, seatId);

            // then
            verify(showSeatRepository, never()).findById(any());
            verify(valueOperations, never()).increment(anyString());
            verify(valueOperations, never()).decrement(anyString());
        }

        @Test
        @DisplayName("owner 키가 이미 없으면 purchase-count는 건드리지 않는다")
        void releaseExpiredHold_noOwner_doesNotTouchPurchaseCount() {
            // given
            UUID showId = UUID.randomUUID();
            UUID seatId = UUID.randomUUID();
            ShowSeat seat = ShowSeat.builder().showId(showId).seatName("A-1").grade("VIP").price(100000).build();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.of(seat));
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);

            // when
            seatService.releaseExpiredHold(showId, seatId);

            // then
            verify(valueOperations).increment("inventory:%s".formatted(showId));
            verify(valueOperations, never()).decrement(anyString());
        }

        @Test
        @DisplayName("만료된 좌석을 찾을 수 없으면 아무 작업도 하지 않는다")
        void releaseExpiredHold_seatNotFound_noOp() {
            // given
            UUID showId = UUID.randomUUID();
            UUID seatId = UUID.randomUUID();

            given(showSeatRepository.findById(seatId)).willReturn(Optional.empty());
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            // when
            seatService.releaseExpiredHold(showId, seatId);

            // then
            verify(valueOperations, never()).increment(anyString());
        }
    }

    @Nested
    @DisplayName("구매 한도 조회")
    class GetPurchaseLimit {

        @Test
        @DisplayName("구매 내역이 없으면 잔여 수량은 한도와 같다")
        void getPurchaseLimit_noPurchase_returnsFullRemaining() {
            // given
            UUID showId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn(null);

            // when
            PurchaseLimitResponse result = seatService.getPurchaseLimit(showId, userId);

            // then
            assertThat(result.limit()).isEqualTo(4);
            assertThat(result.purchased()).isEqualTo(0);
            assertThat(result.remaining()).isEqualTo(4);
        }

        @Test
        @DisplayName("구매 수량만큼 잔여 수량이 차감된다")
        void getPurchaseLimit_withPurchase_returnsReducedRemaining() {
            // given
            UUID showId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn("3");

            // when
            PurchaseLimitResponse result = seatService.getPurchaseLimit(showId, userId);

            // then
            assertThat(result.limit()).isEqualTo(4);
            assertThat(result.purchased()).isEqualTo(3);
            assertThat(result.remaining()).isEqualTo(1);
        }

        @Test
        @DisplayName("한도를 모두 사용했으면 잔여 수량은 0 이하로 내려가지 않는다")
        void getPurchaseLimit_atLimit_returnsZeroRemaining() {
            // given
            UUID showId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(anyString())).willReturn("4");

            // when
            PurchaseLimitResponse result = seatService.getPurchaseLimit(showId, userId);

            // then
            assertThat(result.remaining()).isEqualTo(0);
        }
    }
}
