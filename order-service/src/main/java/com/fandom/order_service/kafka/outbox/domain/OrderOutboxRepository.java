package com.fandom.order_service.kafka.outbox.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

    /** 미발행 레코드를 오래된 순으로 조회. Limit으로 폴링 한 사이클의 배치 크기를 제한한다. */
    List<OrderOutbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Limit limit);
}
