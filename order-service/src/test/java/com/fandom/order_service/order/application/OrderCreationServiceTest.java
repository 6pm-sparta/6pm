package com.fandom.order_service.order.application;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.order.application.creation.OrderCreationResult;
import com.fandom.order_service.order.application.creation.OrderCreationService;
import com.fandom.order_service.order.application.creation.OrderCreationWriter;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.presentation.dto.request.CreateOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCreationService 단위 테스트")
class OrderCreationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCreationWriter orderCreationWriter;

    private OrderCreationService orderCreationService;

    private CreateOrderRequest request;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties(
                new OrderProperties.Hold(30L, 600L), 10,
                new OrderProperties.PaymentLockProperties(3L, 5L, 600L),
<<<<<<< HEAD
                new OrderProperties.Cancellation(24L),
                new OrderProperties.Compensation(3, 1000L), null);
=======
                new OrderProperties.Cancellation(24L), null);
>>>>>>> 48af199 (test: PG 웹훅 인프라 테스트 추가)
        orderCreationService = new OrderCreationService(
                redisTemplate, orderRepository, orderCreationWriter, orderProperties);

        request = new CreateOrderRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 50_000L);
    }

    /**
     * Order.getId()는 BaseEntity의 @PrePersist(assignId)가 실제 영속화될 때만 채워준다.
     * 단위 테스트에서는 Hibernate가 끼지 않아 그 콜백이 안 불리므로, ReflectionTestUtils로
     * private id 필드를 테스트에서만 강제로 채운다 (프로덕션 코드에서의 리플렉션 사용과는 다른,
     * 스프링이 권장하는 테스트 fixture 세팅 방식).
     */
    private Order pendingOrderWithId() {
        Order order = Order.createPending(request.seatId(), request.userId(), request.totalAmount(),
                LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    @Test
    @DisplayName("holdId 클레임에 성공하면 신규 주문을 생성하고 201에 해당하는 결과를 반환한다")
    void createOrder_success() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);

        Order savedOrder = pendingOrderWithId();
        given(orderCreationWriter.insertPendingOrder(
                eq(request.seatId()), eq(request.userId()), eq(request.totalAmount()), any())).willReturn(savedOrder);

        // when
        OrderCreationResult result = orderCreationService.createOrder(request);

        // then
        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.order().seatId()).isEqualTo(request.seatId());
        assertThat(result.order().status()).isEqualTo(OrderStatus.PENDING.name());
        verify(valueOperations).set(anyString(), eq(savedOrder.getId().toString()), any(Duration.class));
    }

    @Test
    @DisplayName("동일 holdId로 재요청이 오고 캐시에 orderId가 있으면 기존 주문을 멱등 응답(200)으로 반환한다")
    void createOrder_duplicateHoldId_returnsCachedOrder() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);

        Order existing = pendingOrderWithId();
        given(valueOperations.get(anyString())).willReturn(existing.getId().toString());
        given(orderRepository.findById(existing.getId())).willReturn(Optional.of(existing));

        // when
        OrderCreationResult result = orderCreationService.createOrder(request);

        // then
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.order().orderId()).isEqualTo(existing.getId());
        verify(orderCreationWriter, never()).insertPendingOrder(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("동일 holdId 요청이 아직 처리 중(클레임 마커)이면 seatId 기준 진행중 주문으로 폴백한다")
    void createOrder_duplicateHoldId_stillProcessing_fallsBackToSeatLookup() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);
        given(valueOperations.get(anyString())).willReturn("CLAIMED"); // UUID로 파싱 불가능한 마커값

        Order activeOrder = pendingOrderWithId();
        given(orderRepository.findFirstBySeatIdAndStatusIn(request.seatId(), OrderStatus.ACTIVE))
                .willReturn(Optional.of(activeOrder));

        // when
        OrderCreationResult result = orderCreationService.createOrder(request);

        // then
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.order().orderId()).isEqualTo(activeOrder.getId());
    }

    @Test
    @DisplayName("Redis 장애(SETNX 실패) 시에도 DB INSERT는 정상 시도된다")
    void createOrder_redisDown_fallsBackToInsert() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        Order savedOrder = pendingOrderWithId();
        given(orderCreationWriter.insertPendingOrder(
                eq(request.seatId()), eq(request.userId()), eq(request.totalAmount()), any())).willReturn(savedOrder);

        // when
        OrderCreationResult result = orderCreationService.createOrder(request);

        // then
        assertThat(result.newlyCreated()).isTrue();
        // Redis가 죽은 경로(holdKey=null)이므로 캐시 저장 자체를 시도하지 않아야 한다.
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("INSERT 시 seatId 부분 UNIQUE 인덱스 충돌이 나면 클레임을 해제하고 기존 진행중 주문을 반환한다")
    void createOrder_seatConflictOnInsert_returnsExistingActiveOrder() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        given(orderCreationWriter.insertPendingOrder(
                eq(request.seatId()), eq(request.userId()), eq(request.totalAmount()), any()))
                .willThrow(new DataIntegrityViolationException("uq_orders_seat_active violated"));

        Order activeOrder = pendingOrderWithId();
        given(orderRepository.findFirstBySeatIdAndStatusIn(request.seatId(), OrderStatus.ACTIVE))
                .willReturn(Optional.of(activeOrder));

        // when
        OrderCreationResult result = orderCreationService.createOrder(request);

        // then
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.order().orderId()).isEqualTo(activeOrder.getId());
        verify(redisTemplate).delete(anyString()); // 클레임 보상 삭제
    }
}