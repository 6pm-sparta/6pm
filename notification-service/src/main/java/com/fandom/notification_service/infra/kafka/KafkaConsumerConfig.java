package com.fandom.notification_service.infra.kafka;

import com.fandom.notification_service.presentation.dto.message.NotificationSendMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // notification.send
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationSendMessage> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, NotificationSendMessage> factory =
                KafkaConsumerSupport.jsonFactory(bootstrapServers, groupId, NotificationSendMessage.class);
        // 처리 실패 시 상세 로그 후 스킵
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "notification.send 처리 실패 - 스킵: topic={}, partition={}, offset={}, key={}, value={}",
                        record.topic(), record.partition(), record.offset(), record.key(), record.value(), ex),
                new FixedBackOff(1000L, 2L)));
        return factory;
    }
}
