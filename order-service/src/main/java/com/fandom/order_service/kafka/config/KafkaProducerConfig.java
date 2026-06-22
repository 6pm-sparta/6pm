package com.fandom.order_service.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * order-service가 발행하는 이벤트용 공통 producer 설정.
 *
 * enable.idempotence: kafka-clients 3.0+부터 기본값이 true라 명시하지 않아도 켜져 있지만,
 * 의도를 분명히 하기 위해 명시한다. 켜지면 retries는 사실상 무제한(delivery.timeout.ms 안에서)이
 * 되므로 retries를 작게 따로 설정하면 오히려 기본값보다 재시도 범위가 줄어든다 — 그래서 따로
 * 건드리지 않는다.
 *
 * max.block.ms: 기본값(60s)이 너무 길다. order-service는 DB 트랜잭션 커밋 직후 요청 스레드에서
 * 동기로 send()를 호출하는 구조라(OrderEventProducer 참고), Kafka가 응답 없으면 결제승인/취소/
 * 확정 API 자체가 그 시간만큼 멈춰버린다. 5초로 줄여 "빠르게 실패"하도록 한다 — 발행 실패 자체는
 * OrderEventProducer가 잡아서 로그만 남기고 비즈니스 흐름은 막지 않는다.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
