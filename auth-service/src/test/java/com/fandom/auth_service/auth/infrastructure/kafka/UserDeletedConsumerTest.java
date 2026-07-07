package com.fandom.auth_service.auth.infrastructure.kafka;

import com.fandom.auth_service.auth.domain.repository.TokenRepository;
import com.fandom.auth_service.auth.infrastructure.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDeletedConsumer 단위 테스트")
class UserDeletedConsumerTest {

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private UserDeletedConsumer consumer;

    @Test
    @DisplayName("user.deleted 이벤트를 받으면 사용자 토큰을 무효화한다")
    void consume_success() {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.getAccessTokenExpiration()).thenReturn(1800000L);

        consumer.consume(new UserDeletedMessage(userId));

        verify(tokenRepository).revokeUserTokens(userId, Duration.ofMillis(1800000L));
    }

    @Test
    @DisplayName("user_id가 없으면 스킵한다")
    void consume_missingUserId() {
        consumer.consume(new UserDeletedMessage(null));

        verify(tokenRepository, never()).revokeUserTokens(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
