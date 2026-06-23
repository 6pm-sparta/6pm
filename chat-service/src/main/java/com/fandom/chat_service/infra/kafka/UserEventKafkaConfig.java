package com.fandom.chat_service.infra.kafka;

import com.fandom.chat_service.presentation.dto.message.CreatorCreatedMessage;
import com.fandom.chat_service.presentation.dto.message.FollowEventMessage;
import com.fandom.chat_service.presentation.dto.message.UserDeletedMessage;
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
public class UserEventKafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // user.creator-created
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CreatorCreatedMessage> creatorCreatedKafkaListenerContainerFactory() {
        return KafkaConsumerSupport.jsonFactory(bootstrapServers, groupId, CreatorCreatedMessage.class);
    }

    // user.followed / user.unfollowed
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FollowEventMessage> followEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, FollowEventMessage> factory =
                KafkaConsumerSupport.jsonFactory(bootstrapServers, groupId, FollowEventMessage.class);
        // 재시도 소진 시 유실 추적용 경고 로그 후 스킵
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                (record, ex) -> log.error(
                        "follow 이벤트 처리 실패 - 스킵: topic={}, partition={}, offset={}, value={}",
                        record.topic(), record.partition(), record.offset(), record.value(), ex),
                new FixedBackOff(2000L, 5L)));
        return factory;
    }

    // user.deleted
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserDeletedMessage> userDeletedKafkaListenerContainerFactory() {
        return KafkaConsumerSupport.jsonFactory(bootstrapServers, groupId, UserDeletedMessage.class);
    }
}
