package com.fandom.user_service.member.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class UserEventKafkaConfig {

    @Bean
    public NewTopic userMemberWithdrawnTopic() {
        return TopicBuilder.name(KafkaTopics.USER_MEMBER_WITHDRAWN).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userCreatorWithdrawnTopic() {
        return TopicBuilder.name(KafkaTopics.USER_CREATOR_WITHDRAWN).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userDeletedTopic() {
        return TopicBuilder.name(KafkaTopics.USER_DELETED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userFollowedTopic() {
        return TopicBuilder.name(KafkaTopics.USER_FOLLOWED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userUnfollowedTopic() {
        return TopicBuilder.name(KafkaTopics.USER_UNFOLLOWED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userCreatorCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.USER_CREATOR_CREATED).partitions(3).replicas(1).build();
    }
}
