package com.fandom.order_service.kafka.outbox.application;

import com.fandom.order_service.kafka.outbox.domain.OrderOutbox;
import com.fandom.order_service.kafka.outbox.domain.OrderOutboxRepository;
import com.fandom.order_service.kafka.outbox.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 폴링 발행자. PENDING 레코드를 읽어 레코드 단위로 발행을 위임한다.
 * topic/payload를 그대로 흘려보내는 "이벤트 종류에 무지한 relay"라 새 이벤트가 추가돼도 손대지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int BATCH_SIZE = 100;

    private final OrderOutboxRepository outboxRepository;
    private final OutboxRecordPublisher recordPublisher;

    @Scheduled(fixedDelayString = "${order.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        List<OrderOutbox> batch =
                outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, Limit.of(BATCH_SIZE));

        for (OrderOutbox record : batch) {
            recordPublisher.publishOne(record.getId());
        }
    }
}
