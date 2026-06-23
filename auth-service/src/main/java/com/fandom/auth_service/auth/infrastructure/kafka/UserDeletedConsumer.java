package com.fandom.auth_service.auth.infrastructure.kafka;

import com.fandom.auth_service.auth.domain.repository.TokenRepository;
import com.fandom.auth_service.auth.infrastructure.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletedConsumer {

    private final TokenRepository tokenRepository;
    private final JwtProvider jwtProvider;

    @KafkaListener(topics = KafkaTopics.USER_DELETED, groupId = "${spring.kafka.consumer.group-id}-user-deleted",
            containerFactory = "userDeletedKafkaListenerContainerFactory")
    public void consume(UserDeletedMessage message) {
        log.info("[{}] 수신 user_id={}", KafkaTopics.USER_DELETED, message.userId());
        if (message.userId() == null) {
            log.warn("[{}] user_id 없음 - 스킵", KafkaTopics.USER_DELETED);
            return;
        }
        tokenRepository.revokeUserTokens(
                message.userId(),
                Duration.ofMillis(jwtProvider.getAccessTokenExpiration())
        );
    }
}
