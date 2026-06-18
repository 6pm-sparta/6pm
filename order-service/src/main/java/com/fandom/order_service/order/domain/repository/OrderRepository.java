package com.fandom.order_service.order.domain.repository;

import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * seatId + 진행중 상태(OrderStatus.ACTIVE) 조합으로 주문을 조회한다.
     * uq_orders_seat_active 부분 UNIQUE 인덱스(수동 마이그레이션 SQL 참고)와 동일한 의미를 가져야 한다.
     * - holdId Redis 캐시가 가리키는 주문을 못 찾았을 때의 폴백 조회
     * - Redis 장애 시 멱등성 폴백 조회
     * - DB UNIQUE 제약 충돌 시, 실제로 이미 존재하는 진행중 주문을 찾아 멱등 응답을 만들기 위한 조회
     */
    Optional<Order> findFirstBySeatIdAndStatusIn(UUID seatId, Collection<OrderStatus> statuses);
}
