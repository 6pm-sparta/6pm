package com.fandom.user_service.member.infrastructure.kafka.outbox.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserOutboxRepository extends JpaRepository<UserOutbox, UUID> {

    /** 미발행 레코드를 오래된 순으로 조회. Limit으로 폴링 한 사이클의 배치 크기를 제한한다. */
    List<UserOutbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Limit limit);
}
