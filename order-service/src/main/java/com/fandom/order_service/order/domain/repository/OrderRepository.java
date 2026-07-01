package com.fandom.order_service.order.domain.repository;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * 비관적 락(SELECT FOR UPDATE) 기준 주문 조회. 결제 요청 처리 중 상태 검증 + 전이 구간에서 사용한다
     * Redis 분산락이 인스턴스 간 동시 요청을 막아주긴 하지만, DB 비관적 락은 그게 뚫렸을 때의 최종 방어선 역할을 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    /**
     * seatId + 진행중 상태(OrderStatus.ACTIVE) 조합으로 주문을 조회한다.
     * uq_orders_seat_active 부분 UNIQUE 인덱스(수동 마이그레이션 SQL 참고)와 동일한 의미를 가져야 한다.
     * - holdId Redis 캐시가 가리키는 주문을 못 찾았을 때의 폴백 조회
     * - Redis 장애 시 멱등성 폴백 조회
     * - DB UNIQUE 제약 충돌 시, 실제로 이미 존재하는 진행중 주문을 찾아 멱등 응답을 만들기 위한 조회
     */
    Optional<Order> findFirstBySeatIdAndStatusIn(UUID seatId, Collection<OrderStatus> statuses);

    /**
     * 유저의 전체 주문 내역 조회 (api 명세서 "주문 목록 조회"). 최신 주문이 먼저 보이도록 호출 측에서
     * createdAt DESC 정렬 Pageable을 넘겨준다 (Repository 메서드 자체는 정렬을 강제하지 않음).
     */
    Page<Order> findByUserId(UUID requesterId, Pageable pageable);

    /**
     * 타임아웃 자동 취소 대상 후보 ID 조회. 락 없이 ID만 가져오고, 실제 락은 건마다
     * OrderTimeoutWriter가 findByIdForUpdate로 개별 획득한다(배치 전체 락 보유 시간 방지).
     */
    @Query("select o.id from Order o where o.status = :status and o.expiredAt < :now")
    List<UUID> findExpiredOrderIds(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now,
            Limit limit);

    /**
     * 환불 복구 배치 재시도 소진 주문 목록. 운영자 수동 검토 대상.
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * 환불 미완료 복구 배치(#96) 대상 후보 ID 조회. status가 REFUND_REQUESTED/FAILED인 주문만 거르고,
     * "FAILED 중 payment가 APPROVED인 것"은 건마다 findByOrderIdAndPaymentStatus로 따로 확인한다.
     * 락 없이 ID만 가져오고, 실제 처리는 건마다 개별 처리한다(배치 전체 락 보유 시간 방지).
     */
    @Query("""
            select o.id from Order o
            where o.status = :refundRequested or o.status = :failed
            """)
    List<UUID> findRefundRecoveryCandidateOrderIds(
            @Param("refundRequested") OrderStatus refundRequested,
            @Param("failed") OrderStatus failed,
            Limit limit);
}
