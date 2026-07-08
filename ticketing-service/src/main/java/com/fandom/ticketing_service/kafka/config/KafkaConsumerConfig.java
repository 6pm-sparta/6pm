package com.fandom.ticketing_service.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * PaymentEventConsumer는 @Payload String을 ObjectMapper로 직접 역직렬화하는 구조라
 * 역직렬화 실패가 곧 리스너 메서드의 RuntimeException으로 드러난다. DefaultErrorHandler 없이는
 * 기본 정책(무한 재시도)에 걸려 처리 못 하는 메시지 하나가 해당 파티션을 영구히 막는다 —
 * 짧게 재시도한 뒤(FixedBackOff) DeadLetterPublishingRecoverer가 {topic}.DLQ로 옮겨서 유실 없이
 * 다음 메시지로 넘어가도록 한다(order-service KafkaConsumerConfig와 동일 패턴).
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> dlqKafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqKafkaTemplate,
                (record, ex) -> {
                    String dlqTopic = record.topic() + ".DLQ";
                    log.error("[Kafka DLQ] 재시도 소진 → DLQ 이동. topic={}, dlqTopic={}, offset={}, key={}",
                            record.topic(), dlqTopic, record.offset(), record.key(), ex);
                    return new TopicPartition(dlqTopic, -1);
                });

        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
