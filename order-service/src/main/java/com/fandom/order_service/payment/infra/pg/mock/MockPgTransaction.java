package com.fandom.order_service.payment.infra.pg.mock;

import com.fandom.common.entity.BaseEntity;
import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.order_service.payment.infra.pg.PgTransactionResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Mock PG 자체의 거래 기록(PG사 입장에서 본 진짜 상태). webhook 발송 여부와 무관하게 저장된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mock_pg_transactions")
public class MockPgTransaction extends BaseEntity {

    @Column(nullable = false, length = 100, unique = true)
    private String pgTransactionId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PgTransactionResult status;

    @Column(length = 255)
    private String failureReason;

    @Builder
    private MockPgTransaction(String pgTransactionId, UUID orderId, Long amount,
                              PgTransactionResult status, String failureReason) {
        this.pgTransactionId = pgTransactionId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
    }

    /** 결제 승인 완료 시 호출. */
    public static MockPgTransaction approved(String pgTransactionId, UUID orderId, Long amount) {
        return MockPgTransaction.builder()
                .pgTransactionId(pgTransactionId)
                .orderId(orderId)
                .amount(amount)
                .status(PgTransactionResult.APPROVED)
                .build();
    }

    /** 결제 거절 시 호출. */
    public static MockPgTransaction failed(String pgTransactionId, UUID orderId, Long amount, String failureReason) {
        return MockPgTransaction.builder()
                .pgTransactionId(pgTransactionId)
                .orderId(orderId)
                .amount(amount)
                .status(PgTransactionResult.FAILED)
                .failureReason(failureReason)
                .build();
    }

    /** APPROVED/REFUND_FAILED → REFUNDED. REFUND_FAILED 허용은 재시도 지원 목적. */
    public void markRefunded() {

        if (this.status == PgTransactionResult.REFUNDED) {
            return;
        }

        if (this.status != PgTransactionResult.APPROVED && this.status != PgTransactionResult.REFUND_FAILED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = PgTransactionResult.REFUNDED;
    }

    /** APPROVED/REFUND_FAILED → REFUND_FAILED. */
    public void markRefundFailed(String failureReason) {

        if (this.status != PgTransactionResult.APPROVED && this.status != PgTransactionResult.REFUND_FAILED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.status = PgTransactionResult.REFUND_FAILED;
        this.failureReason = failureReason;
    }
}
