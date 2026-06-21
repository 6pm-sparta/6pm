package com.fandom.order_service.payment.domain.repository;

import com.fandom.order_service.payment.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
