package com.fandom.feed.infra.redis;

import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Like;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.LikeRepository;
import com.fandom.feed.global.constant.RedisKeyPrefix;
import com.fandom.feed.infra.redis.dto.PostCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactionCacheServiceTest {
    @Mock
    private PostReader postReader;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private ReactionCacheService reactionCacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Nested
    @DisplayName("리액션 정보 조회")
    class GetReactionInfo {
        @Test
        @DisplayName("userId 있음 - commentCount, likeCount, liked 정상 매핑")
        void getReactionInfoWithUserId() {
            // Given
            UUID postId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(valueOperations.get(anyString())).thenReturn("10");
            when(setOperations.size(anyString())).thenReturn(5L);
            when(setOperations.isMember(anyString(), anyString())).thenReturn(true);

            // When
            PostCache.ReactionInfo info = reactionCacheService.getReactionInfo(postId, userId);

            // Then
            assertThat(info.commentCount()).isEqualTo(10L);
            assertThat(info.likeCount()).isEqualTo(5L);
            assertThat(info.liked()).isTrue();
        }

        @Test
        @DisplayName("userId 없음 - liked 항상 false")
        void getReactionInfoWithoutUserId() {
            // Given
            UUID postId = UUID.randomUUID();

            when(valueOperations.get(anyString())).thenReturn("10");
            when(setOperations.size(anyString())).thenReturn(5L);

            // When
            PostCache.ReactionInfo info = reactionCacheService.getReactionInfo(postId, null);

            // Then
            assertThat(info.commentCount()).isEqualTo(10L);
            assertThat(info.likeCount()).isEqualTo(5L);
            assertThat(info.liked()).isFalse();
        }
    }

    @Nested
    @DisplayName("댓글 수 조회")
    class GetCommentCount {
        @Test
        @DisplayName("캐시 히트 - DB 조회 없이 반환")
        void getCommentCountCacheHit() {
            // Given
            UUID postId = UUID.randomUUID();
            when(valueOperations.get(RedisKeyPrefix.COMMENT_COUNT + postId)).thenReturn("7");

            // When
            PostCache.ReactionInfo info = reactionCacheService.getReactionInfo(postId, null);

            // Then
            assertThat(info.commentCount()).isEqualTo(7L);
            verify(postReader, never()).findById(any());
        }

        @Test
        @DisplayName("캐시 미스 - DB 조회 후 캐시 저장")
        void getCommentCountCacheMiss() {
            // Given
            UUID postId = UUID.randomUUID();
            Post post = mock(Post.class);
            when(post.getCommentCount()).thenReturn(3L);
            when(valueOperations.get(RedisKeyPrefix.COMMENT_COUNT + postId)).thenReturn(null);
            when(postReader.findById(postId)).thenReturn(post);
            // getLikeCount의 commentKey null 체크도 통과시켜야 함
            when(setOperations.size(anyString())).thenReturn(0L);

            // When
            PostCache.ReactionInfo info = reactionCacheService.getReactionInfo(postId, null);

            // Then
            assertThat(info.commentCount()).isEqualTo(3L);
            verify(valueOperations).set(eq(RedisKeyPrefix.COMMENT_COUNT + postId), eq("3"), anyLong(), eq(TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("좋아요 수 조회")
    class GetLikeCount {
        @Test
        @DisplayName("캐시 히트 - DB 조회 없이 반환")
        void getLikeCountCacheHit() {
            // Given
            UUID postId = UUID.randomUUID();
            when(valueOperations.get(RedisKeyPrefix.COMMENT_COUNT + postId)).thenReturn("5"); // 로드 여부 확인용
            when(setOperations.size(RedisKeyPrefix.LIKE_SET + postId)).thenReturn(3L);

            // When
            PostCache.ReactionInfo info = reactionCacheService.getReactionInfo(postId, null);

            // Then
            assertThat(info.likeCount()).isEqualTo(3L);
            verify(likeRepository, never()).findAllByPostId(any());
        }

        @Test
        @DisplayName("캐시 미스 - DB 조회 후 캐시 저장")
        void getLikeCountCacheMiss() {
            // Given
            UUID postId = UUID.randomUUID();
            UUID likeUserId = UUID.randomUUID();
            Like like = mock(Like.class);
            when(like.getUserId()).thenReturn(likeUserId);

            when(valueOperations.get(RedisKeyPrefix.COMMENT_COUNT + postId)).thenReturn(null);
            when(postReader.findById(postId)).thenReturn(mock(Post.class));
            when(likeRepository.findAllByPostId(postId)).thenReturn(List.of(like));

            // When
            PostCache.ReactionInfo info = reactionCacheService.getReactionInfo(postId, null);

            // Then
            assertThat(info.likeCount()).isEqualTo(1L);
            verify(setOperations).add(eq(RedisKeyPrefix.LIKE_SET + postId), any(String[].class));
        }
    }
}