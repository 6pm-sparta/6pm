package com.fandom.order_service.order.presentation.controller;

import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.config.CommonAuthAutoConfiguration;
import com.fandom.common.auth.filter.IdCardVerificationFilter;
import com.fandom.common.exception.GlobalExceptionHandler;
import com.fandom.order_service.order.application.cancellation.OrderCancelService;
import com.fandom.order_service.order.application.query.OrderQueryService;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.presentation.dto.response.OrderCancelResponse;
import com.fandom.order_service.order.presentation.dto.response.OrderResponse;
import com.fandom.order_service.order.presentation.dto.response.OrderSummaryResponse;
import com.fandom.order_service.order.presentation.dto.response.PageResponse;
import com.fandom.common.exception.CustomException;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderController 슬라이스 테스트.
 *
 * {@code @CurrentIdCard}는 IdCardVerificationFilter가 request attribute("userIdCard")에 저장한 값을
 * CurrentIdCardArgumentResolver가 꺼내 주입하는 구조다. 여기서는 HMAC 서명 검증까지 태우지 않고,
 * MockMvc의 requestAttr()로 그 attribute를 직접 주입해 컨트롤러/서비스 연결과 어노테이션 동작만 검증한다.
 * CommonAuthAutoConfiguration을 명시적으로 Import해야 {@code @CurrentIdCard} 리졸버가 등록된다 — 등록을
 * 빠뜨리면 모든 요청이 401(UNAUTHORIZED)로 떨어지는 회귀가 있었기 때문에, 이 테스트가 그 회귀를 잡아준다.
 */
@WebMvcTest(OrderController.class)
@Import({CommonAuthAutoConfiguration.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "hmac.secret-key=test-secret-key")
@DisplayName("OrderController 슬라이스 테스트")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private OrderCancelService orderCancelService;

    @Test
    @DisplayName("단건 조회 성공 시 200과 주문 정보를 반환한다")
    void getOrder_success() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId, UUID.randomUUID(), userId, "PENDING", 50_000L,
                LocalDateTime.now());
        given(orderQueryService.getOrder(eq(orderId), eq(userId))).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("본인 주문이 아니면 403을 반환한다")
    void getOrder_forbidden() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        given(orderQueryService.getOrder(eq(orderId), eq(requesterId)))
                .willThrow(new CustomException(OrderErrorCode.ORDER_ACCESS_DENIED));

        // when & then
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(requesterId, "MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 주문이면 404를 반환한다")
    void getOrder_notFound() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        given(orderQueryService.getOrder(eq(orderId), eq(requesterId)))
                .willThrow(new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(requesterId, "MEMBER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증 정보(userIdCard attribute)가 없으면 401을 반환한다")
    void getOrder_unauthorized_whenNoIdCard() throws Exception {
        // when & then — requestAttr 없이 호출 (인증 정보 없음)
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("page/size 파라미터 없이 호출하면 기본값 0/20이 서비스로 전달된다")
    void getOrders_defaultPaging() throws Exception {
        // given
        UUID requesterId = UUID.randomUUID();
        PageResponse<OrderSummaryResponse> emptyResponse =
                new PageResponse<>(List.of(), 0, 20, 0L, 0);
        given(orderQueryService.getOrders(eq(requesterId), eq(0), eq(20))).willReturn(emptyResponse);

        // when & then
        mockMvc.perform(get("/api/v1/orders")
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(requesterId, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("page/size를 명시하면 그 값이 그대로 서비스로 전달된다")
    void getOrders_explicitPaging() throws Exception {
        // given
        UUID requesterId = UUID.randomUUID();
        PageResponse<OrderSummaryResponse> response =
                new PageResponse<>(List.of(), 2, 5, 0L, 0);
        given(orderQueryService.getOrders(eq(requesterId), eq(2), eq(5))).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "2")
                        .param("size", "5")
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(requesterId, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    @DisplayName("PENDING 주문 취소는 200과 CANCELLED 상태를 반환하고 paymentId는 응답에 없다")
    void cancelOrder_pending_returnsCancelled() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrderCancelResponse response = OrderCancelResponse.withoutRefund(
                orderId, OrderStatus.CANCELLED, LocalDateTime.now());
        given(orderCancelService.cancelOrder(eq(orderId), eq(userId))).willReturn(response);

        // when & then
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentId").doesNotExist());
    }

    @Test
    @DisplayName("PAID 주문 취소는 200과 REFUNDED 상태 + paymentId를 반환한다")
    void cancelOrder_paid_returnsRefundedWithPaymentId() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        OrderCancelResponse response = OrderCancelResponse.refunded(
                orderId, OrderStatus.REFUNDED, paymentId, LocalDateTime.now());
        given(orderCancelService.cancelOrder(eq(orderId), eq(userId))).willReturn(response);

        // when & then
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()));
    }

    @Test
    @DisplayName("취소 불가 상태면 409를 반환한다")
    void cancelOrder_invalidStatus_returns409() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(orderCancelService.cancelOrder(eq(orderId), eq(userId)))
                .willThrow(new CustomException(OrderErrorCode.INVALID_ORDER_STATUS));

        // when & then
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CONFIRMED 취소 가능 시간이 지났으면 409를 반환한다")
    void cancelOrder_windowExpired_returns409() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(orderCancelService.cancelOrder(eq(orderId), eq(userId)))
                .willThrow(new CustomException(OrderErrorCode.CANCELLATION_WINDOW_EXPIRED));

        // when & then
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PG 환불 실패 시 502를 반환한다")
    void cancelOrder_pgError_returns502() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(orderCancelService.cancelOrder(eq(orderId), eq(userId)))
                .willThrow(new CustomException(PaymentErrorCode.PG_ERROR));

        // when & then
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("본인 주문이 아니면 취소 요청도 403을 반환한다")
    void cancelOrder_notOwner_returns403() throws Exception {
        // given
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(orderCancelService.cancelOrder(eq(orderId), eq(userId)))
                .willThrow(new CustomException(OrderErrorCode.ORDER_ACCESS_DENIED));

        // when & then
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .requestAttr(IdCardVerificationFilter.ID_CARD_ATTRIBUTE, UserIdCard.of(userId, "MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 정보 없이 취소 요청하면 401을 반환한다")
    void cancelOrder_unauthorized_whenNoIdCard() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}