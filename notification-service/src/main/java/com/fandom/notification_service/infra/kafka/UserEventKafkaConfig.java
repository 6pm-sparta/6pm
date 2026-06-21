package com.fandom.notification_service.infra.kafka;

import com.fandom.notification_service.presentation.dto.message.UserDeletedMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

@EnableKafka
@Configuration
public class UserEventKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // user.deleted
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserDeletedMessage> userDeletedKafkaListenerContainerFactory() {
        return KafkaConsumerSupport.jsonFactory(bootstrapServers, groupId, UserDeletedMessage.class);
    }
}
