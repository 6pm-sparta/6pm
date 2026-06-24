package com.fandom.aiops_service.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * incident.detected 토픽 선언.
 * 로컬(KRaft 단일 브로커)에서는 partitions=1 / replicas=1 로 충분.
 * 운영(MSK 등)에서는 replicas>=2, partitions 는 처리량에 맞춰 조정.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic incidentDetectedTopic(
            @Value("${aiops.kafka.topic.incident-detected:incident.detected}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
