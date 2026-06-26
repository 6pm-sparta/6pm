package com.fandom.feed.infra.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    boolean existsByEventKey(String eventKey);
}