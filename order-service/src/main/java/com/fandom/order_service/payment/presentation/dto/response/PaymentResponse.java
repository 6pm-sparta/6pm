package com.fandom.order_service.payment.presentation.dto.response;

import com.fandom.order_service.payment.domain.entity.Payment;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        Long amount,
        String paymentStatus,
        String paymentMethod,
        String pgTransactionId,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getPaymentStatus().name(),
                payment.getPaymentMethod().name(),
                payment.getPgTransactionId(),
                payment.getCreatedAt()
        );
    }
}
