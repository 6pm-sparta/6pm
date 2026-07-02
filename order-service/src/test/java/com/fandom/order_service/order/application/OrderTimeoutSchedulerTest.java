package com.fandom.order_service.order.application;

import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.order.application.timeout.OrderTimeoutResult;
import com.fandom.order_service.order.application.timeout.OrderTimeoutScheduler;
import com.fandom.order_service.order.application.timeout.OrderTimeoutWriter;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTimeoutScheduler 단위 테스트")
class OrderTimeoutSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderTimeoutWriter orderTimeoutWriter;

    private OrderTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties(
                null, 10, null, null, null, null,
                new OrderProperties.Timeout(100, 5000L), null, null);
        scheduler = new OrderTimeoutScheduler(orderRepository, orderTimeoutWriter, orderProperties);
    }

    @Test
    @DisplayName("만료 후보가 없으면 OrderTimeoutWriter를 호출하지 않는다")
    void expireTimedOutOrders_noCandidates_doesNothing() {
        // given
        given(orderRepository.findExpiredOrderIds(eq(OrderStatus.PENDING), any(), any()))
                .willReturn(List.of());

        // when
        scheduler.expireTimedOutOrders();

        // then
        verify(orderTimeoutWriter, never()).expireIfStillPending(any());
    }

    @Test
    @DisplayName("후보 전부 처리 위임한다")
    void expireTimedOutOrders_processesEachCandidate() {
        // given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        given(orderRepository.findExpiredOrderIds(eq(OrderStatus.PENDING), any(), any()))
                .willReturn(List.of(id1, id2));
        given(orderTimeoutWriter.expireIfStillPending(any())).willReturn(OrderTimeoutResult.CANCELLED);

        // when
        scheduler.expireTimedOutOrders();

        // then
        verify(orderTimeoutWriter).expireIfStillPending(id1);
        verify(orderTimeoutWriter).expireIfStillPending(id2);
    }

    @Test
    @DisplayName("한 건에서 예외가 발생해도 나머지 건은 계속 처리한다 — " +
            "배치 중 일부 실패가 전체 배치를 막으면 안 됨")
    void expireTimedOutOrders_oneFails_othersStillProcessed() {
        // given
        UUID failingId = UUID.randomUUID();
        UUID succeedingId = UUID.randomUUID();
        given(orderRepository.findExpiredOrderIds(eq(OrderStatus.PENDING), any(), any()))
                .willReturn(List.of(failingId, succeedingId));
        given(orderTimeoutWriter.expireIfStillPending(failingId))
                .willThrow(new RuntimeException("DB 커넥션 오류"));
        given(orderTimeoutWriter.expireIfStillPending(succeedingId))
                .willReturn(OrderTimeoutResult.CANCELLED);

        // when
        scheduler.expireTimedOutOrders();

        // then
        verify(orderTimeoutWriter, times(1)).expireIfStillPending(failingId);
        verify(orderTimeoutWriter, times(1)).expireIfStillPending(succeedingId);
    }
}
