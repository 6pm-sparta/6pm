package com.fandom.order_service.kafka.config;

import com.fandom.order_service.kafka.event.SeatBookFailedEvent;
import com.fandom.order_service.kafka.event.SeatBookedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * order-service가 수신하는 ticketing-service 이벤트용 consumer 설정.
 * ErrorHandlingDeserializer로 감싸 역직렬화 실패가 리스너 스레드를 죽이지 않게 하고,
 * DefaultErrorHandler가 짧게 재시도한 뒤 (FixedBackOff) 그래도 실패하면 로그만 남기고 다음 메시지로 넘어간다.
 * — 처리 못 하는 메시지 하나가 뒤따르는 메시지 처리를 영구히 막아서는 걸 방지한다.
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public DefaultErrorHandler errorHandler() {
        return new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "[Kafka] 메시지 처리 실패 - 스킵: topic={}, partition={}, offset={}, key={}, value={}",
                        record.topic(), record.partition(), record.offset(), record.key(), record.value(), ex),
                new FixedBackOff(1000L, 2L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SeatBookedEvent> seatBookedKafkaListenerContainerFactory(
            DefaultErrorHandler errorHandler) {
        return containerFactory(SeatBookedEvent.class, errorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SeatBookFailedEvent> seatBookFailedKafkaListenerContainerFactory(
            DefaultErrorHandler errorHandler) {
        return containerFactory(SeatBookFailedEvent.class, errorHandler);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> containerFactory(
            Class<T> targetType, DefaultErrorHandler errorHandler) {

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, targetType.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.fandom.order_service.kafka.event");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}