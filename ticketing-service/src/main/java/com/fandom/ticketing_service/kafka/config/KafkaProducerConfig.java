package com.fandom.ticketing_service.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * ticketing-service가 발행하는 이벤트용 공통 producer 설정.
 *
 * kafkaTemplate(Primary): 비즈니스 이벤트 발행용. order-service의 KafkaProducerConfig와 동일한
 * 의도로 enable.idempotence를 명시하고, max.block.ms를 5초로 줄여 Kafka 장애 시 좌석 Hold/확정
 * API가 60초 기본값만큼 멈추는 것을 막는다. 발행 실패 자체는 SeatEventProducer가 흡수하므로
 * 비즈니스 흐름은 막지 않는다.
 * dlqKafkaTemplate: KafkaConsumerConfig의 DeadLetterPublishingRecoverer 전용. ticketing 컨슈머는
 * ErrorHandlingDeserializer 없이 String 그대로 받아 ObjectMapper로 수동 역직렬화하므로(PaymentEventConsumer),
 * DLQ에도 원본 문자열 그대로 실어 보낼 수 있게 String 직렬화를 쓴다. idempotence는 order-service와
 * 동일한 이유(임의 토픽 쓰기)로 비활성화.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Primary
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

    @Bean
    public KafkaTemplate<String, String> dlqKafkaTemplate() {

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
