package com.fandom.feed.infra.redis;

import com.fandom.feed.infra.redis.dto.PostCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostReactionServiceTest {
    @Mock
    private RedisTemplate<String, String> redisTemplate; // 혹은 사용하는 템플릿 타입
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SetOperations<String, String> setOperations;

    @InjectMocks
    private PostReactionService postReactionService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("리액션 정보 조회")
    void getReactionInfoTest() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(valueOperations.get(anyString())).thenReturn("10");
        when(setOperations.size(anyString())).thenReturn(5L);
        when(setOperations.isMember(anyString(), anyString())).thenReturn(true);

        // When
        PostCache.ReactionInfo info = postReactionService.getReactionInfo(postId, userId);

        // Then
        assertThat(info.commentCount()).isEqualTo(10L);
        assertThat(info.likeCount()).isEqualTo(5L);
        assertThat(info.liked()).isTrue();
    }
}