package com.fandom.order_service.payment.infra.pg;

import java.util.UUID;

/** 거래조회(inquireTransaction) 결과. PG가 실제로 처리한 거래의 진짜 상태. */
public record PgTransactionStatus(
        String pgTransactionId,
        UUID orderId,
        Long amount,
        PgTransactionResult status,
        String failureReason
) {
}
