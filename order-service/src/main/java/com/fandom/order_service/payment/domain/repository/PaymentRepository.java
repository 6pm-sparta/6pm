package com.fandom.order_service.payment.domain.repository;

import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.payment.domain.entity.Payment;
import com.fandom.order_service.payment.domain.entity.PaymentStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Idempotency-Key 기준 조회. Redis 멱등성 키 1차 방어가 뚫렸을 때(장애, TTL 만료 등)
     * DB UNIQUE 제약(idempotency_key) 기반 2차 방어 폴백 조회용.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * 주문별 결제 시도 이력 전체 조회. 결제 단건 조회/환불 시 pg_transaction_id 확인 등에 사용.
     * 최신 시도가 먼저 보이도록 호출 측에서 정렬된 결과가 필요하면 별도 정렬 파라미터를 추가한다.
     */
    List<Payment> findByOrderId(UUID orderId);

    /**
     * 주문의 결제 시도 전체 목록(최신 시도 먼저). 주문별 결제 조회에서 사용.
     */
    List<Payment> findByOrderIdOrderByCreatedAtDescIdDesc(UUID orderId);

    /**
     * 주문의 승인된 결제 건 조회. 주문 취소(환불) 시 pg_transaction_id를 얻기 위해 사용한다.
     */
    Optional<Payment> findByOrderIdAndPaymentStatus(UUID orderId, PaymentStatus paymentStatus);

    /**
     * 진행중(REQUESTED) 결제 존재 여부.
     * PaymentRequestWriter.markPaymentRequestedAndSave에서 동시 결제 요청 차단에 사용한다.
     */
    boolean existsByOrderIdAndPaymentStatus(UUID orderId, PaymentStatus paymentStatus);

    /**
     * PG 콜백(webhook)이 들고 오는 pgTransactionId로 결제 시도를 찾는다.
     */
    Optional<Payment> findByPgTransactionId(String pgTransactionId);

    /** retryable=true인 FAILED Payment를 가진 주문 ID 조회. 건별 처리는 Writer가 비관적 락으로 처리. */
    @Query("""
            select distinct p.orderId from Payment p
            where p.paymentStatus = :status
              and p.retryable = true
            """)
    List<UUID> findRetryableOrderIds(@Param("status") PaymentStatus status, Limit limit);

    /** orderId 기준 전체 결제 시도 횟수. 재시도 횟수 초과 판별에 사용. */
    long countByOrderId(UUID orderId);

    /** PAID 전이 시 해당 주문의 retryable 플래그 일괄 해제. 폴링 대상 누적 방지. */
    @Modifying
    @Query("update Payment p set p.retryable = false where p.orderId = :orderId and p.retryable = true")
    void clearRetryableFlagByOrderId(@Param("orderId") UUID orderId);

    /**
     * 좀비 결제 후보 조회. REQUESTED로 멈춘 Payment 중 연관 Order가 PENDING이고 expired_at이 지난 것.
     * 락 없이 orderId만 가져오고, 실제 처리는 건마다 ZombiePaymentRecoveryWriter가 비관적 락으로 처리한다.
     */
    @Query("""
            select p.orderId from Payment p
            where p.paymentStatus = :requested
              and exists (
                  select 1 from Order o
                  where o.id = p.orderId
                    and o.status = :pending
                    and o.expiredAt < :now
              )
            """)
    List<UUID> findZombiePaymentOrderIds(
            @Param("requested") PaymentStatus requested,
            @Param("pending") OrderStatus pending,
            @Param("now") LocalDateTime now,
            Limit limit);
}
