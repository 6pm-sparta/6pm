package com.fandom.order_service.order.application;

import com.fandom.common.exception.CustomException;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.presentation.dto.response.OrderSummaryResponse;
import com.fandom.order_service.order.presentation.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService 단위 테스트")
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderQueryService orderQueryService;

    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        orderQueryService = new OrderQueryService(orderRepository);
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    private Order orderOwnedBy(UUID userId) {
        Order order = Order.createPending(UUID.randomUUID(), userId, 50_000L, LocalDateTime.now().plusMinutes(10));
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    @Test
    @DisplayName("본인 주문이면 단건 조회에 성공한다")
    void getOrder_success() {
        // given
        Order order = orderOwnedBy(ownerId);
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        // when
        var response = orderQueryService.getOrder(order.getId(), ownerId);

        // then
        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.userId()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND(404)를 던진다")
    void getOrder_notFound() {
        // given
        UUID orderId = UUID.randomUUID();
        given(orderRepository.findById(orderId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderQueryService.getOrder(orderId, ownerId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 주문이 아니면 ORDER_ACCESS_DENIED(403)를 던진다")
    void getOrder_forbidden() {
        // given
        Order order = orderOwnedBy(ownerId);
        given(orderRepository.findById(order.getId())).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderQueryService.getOrder(order.getId(), otherUserId))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("목록 조회는 createdAt DESC 정렬로 본인 주문만 페이징하여 반환한다")
    void getOrders_success() {
        // given
        Order order1 = orderOwnedBy(ownerId);
        Order order2 = orderOwnedBy(ownerId);
        Page<Order> page = new PageImpl<>(List.of(order1, order2), PageRequest.of(0, 20), 2);

        given(orderRepository.findByUserId(eq(ownerId), any(Pageable.class))).willReturn(page);

        // when
        PageResponse<OrderSummaryResponse> response = orderQueryService.getOrders(ownerId, 0, 20);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("조회 결과가 없으면 빈 content와 totalElements=0을 반환한다")
    void getOrders_emptyResult() {
        // given
        Page<Order> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        given(orderRepository.findByUserId(eq(ownerId), any(Pageable.class))).willReturn(emptyPage);

        // when
        PageResponse<OrderSummaryResponse> response = orderQueryService.getOrders(ownerId, 0, 20);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    @DisplayName("page가 음수면 INVALID_INPUT_VALUE(400)를 던진다")
    void getOrders_negativePage_throws() {
        assertThatThrownBy(() -> orderQueryService.getOrders(ownerId, -1, 20))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("size가 0 이하면 INVALID_INPUT_VALUE(400)를 던진다")
    void getOrders_nonPositiveSize_throws() {
        assertThatThrownBy(() -> orderQueryService.getOrders(ownerId, 0, 0))
                .isInstanceOf(CustomException.class);
    }
}
