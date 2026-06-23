package com.fandom.order_service.payment.application.query;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.common.exception.ErrorCode;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.exception.OrderErrorCode;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.exception.PaymentErrorCode;
import com.fandom.order_service.payment.domain.repository.PaymentRepository;
import com.fandom.order_service.payment.presentation.dto.response.PaymentDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 결제 조회. 단건/주문별 목록 모두 호출자의 userId와 "결제가 속한 주문의 소유자"가 일치하는지
 * 검증한다(본인 주문의 결제만 조회 가능). 조회용이라 락을 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 결제 시도 단건 상세 조회. 본인 주문의 결제가 아니면 PAYMENT_ACCESS_DENIED(403),
     * 존재하지 않으면 PAYMENT_NOT_FOUND(404).
     */
    public PaymentDetailResponse getPayment(UUID paymentId, UUID requesterId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        // FK로 보장되는 관계라 정상 흐름에서는 절대 비어있을 수 없는 방어적 체크
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR));

        validateOwner(order, requesterId, PaymentErrorCode.PAYMENT_ACCESS_DENIED);

        return PaymentDetailResponse.from(payment);
    }

    /**
     * 주문의 결제 시도 전체 목록 조회(최신 시도 먼저). 결제 시도가 0건(예: 아직 결제 전 PENDING 주문)이면
     * 빈 배열을 200으로 반환한다 — 정상 상태이므로 404가 아니다. 본인 주문이 아니면 ORDER_ACCESS_DENIED(403),
     * 주문이 없으면 ORDER_NOT_FOUND(404).
     */
    public List<PaymentDetailResponse> getPaymentsByOrder(UUID orderId, UUID requesterId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));

        validateOwner(order, requesterId, OrderErrorCode.ORDER_ACCESS_DENIED);

        return paymentRepository.findByOrderIdOrderByCreatedAtDescIdDesc(orderId).stream()
                .map(PaymentDetailResponse::from)
                .toList();
    }

    private void validateOwner(Order order, UUID requesterId, ErrorCode accessDeniedCode) {
        if (!order.getUserId().equals(requesterId)) {
            throw new CustomException(accessDeniedCode);
        }
    }
}
