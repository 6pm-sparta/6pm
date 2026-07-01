package com.fandom.order_service.payment.domain.entity;

import com.fandom.common.entity.BaseEntity;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 결제 시도 이력(payments) 엔티티. 주문 하나당 결제 시도가 여러 번 있을 수 있다(1:N).
 * 실패 시 retry_count를 올리는 대신 새 레코드를 INSERT하는 방식 — 시도별 실패 사유/PG 승인번호를
 * 전부 추적하기 위함.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order_id", columnList = "orderId")
})
public class Payment extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    /** PG사 발급 트랜잭션 ID. 결제 실패 시 PG가 승인번호를 내려주지 않으므로 nullable. */
    @Column(length = 100)
    private String pgTransactionId;

    /**
     * 중복 결제 방지 키(클라이언트 생성 Idempotency-Key 헤더 값).
     * Redis 1차 방어 + 이 컬럼의 DB UNIQUE 제약이 2차 방어 역할을 한다.
     */
    @Column(nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long refundAmount;

    /** 환불 자동 재시도 횟수. */
    @Column(nullable = false)
    private Long refundRetryCount;

    /** PG사 응답 실패 사유. 성공 시 null. */
    @Column(length = 255)
    private String failureReason;

    @Builder
    private Payment(UUID orderId, Long amount, PaymentStatus paymentStatus, PaymentMethod paymentMethod,
                    String pgTransactionId, String idempotencyKey, Long refundAmount, Long refundRetryCount,
                    String failureReason) {
        this.orderId = orderId;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
        this.paymentMethod = paymentMethod;
        this.pgTransactionId = pgTransactionId;
        this.idempotencyKey = idempotencyKey;
        this.refundAmount = refundAmount != null ? refundAmount : 0L;
        this.refundRetryCount = refundRetryCount != null ? refundRetryCount : 0L;
        this.failureReason = failureReason;
    }

    /**
     * 요청 접수(REQUESTED) 직후 PG가 즉시 반환한 거래 식별자를 기록한다.
     */
    public void recordPgTransactionId(String pgTransactionId) {

        if (this.paymentStatus != PaymentStatus.REQUESTED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.pgTransactionId = pgTransactionId;
    }

    /**
     * REQUESTED → APPROVED. PG 승인 webhook을 받은 직후 호출한다. pgTransactionId는
     * recordPgTransactionId()로 이미 기록돼 있으므로 여기서 다시 받지 않는다.
     *
     * @throws CustomException REQUESTED가 아닌 상태에서 호출되면 발생. 정상 흐름에서는 절대 발생하지
     *         않아야 하는 방어적 체크라(서비스 레이어가 항상 REQUESTED로 막 생성한 Payment에 대해서만
     *         호출함) CommonErrorCode.INTERNAL_SERVER_ERROR로 처리한다(비즈니스 409가 아니라 서버 내부 불일치).
     */
    public void approve() {

        if (this.paymentStatus != PaymentStatus.REQUESTED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.paymentStatus = PaymentStatus.APPROVED;
    }

    /** REQUESTED → FAILED. PG 거절/오류 응답을 받은 직후 호출한다.*/
    public void fail(String failureReason) {

        if (this.paymentStatus != PaymentStatus.REQUESTED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.paymentStatus = PaymentStatus.FAILED;
        this.failureReason = failureReason;
    }

    /**
     * APPROVED → REFUNDED. PG 환불 성공 응답을 받은 직후 호출한다. 전액 환불만 지원한다(MVP, 부분 환불 없음).
     */
    public void refund() {

        if (this.paymentStatus != PaymentStatus.APPROVED) {
            throw new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        this.paymentStatus = PaymentStatus.REFUNDED;
        this.refundAmount = this.amount;
    }

    /** 재환불 시도마다 호출. */
    public void increaseRefundRetryCount() {
        this.refundRetryCount++;
    }

    /** 재시도 소진 여부. */
    public boolean hasExhaustedRefundRetries(int maxRetries) {
        return this.refundRetryCount >= maxRetries;
    }
}
