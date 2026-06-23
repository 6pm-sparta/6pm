package com.fandom.order_service.payment.presentation.dto.response;

import com.fandom.order_service.payment.domain.entity.Payment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 시도 조회 응답 (단건 조회 / 주문별 목록 조회 공용).
 */
public record PaymentDetailResponse(
        UUID paymentId,
        UUID orderId,
        Long amount,
        String paymentStatus,
        String paymentMethod,
        String pgTransactionId,
        Long refundAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        return new PaymentDetailResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getPaymentStatus().name(),
                payment.getPaymentMethod().name(),
                payment.getPgTransactionId(),
                payment.getRefundAmount(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
