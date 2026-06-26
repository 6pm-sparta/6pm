package com.fandom.feed.infra.redis;

import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.application.ImageService;
import com.fandom.feed.application.PostReader;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.infra.redis.constant.RedisKeyPrefix;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.infra.s3.util.ImageUrlConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostDetailCacheServiceTest {
    @Mock
    private PostReader postReader;

    @Mock
    private ImageService imageService;

    @Mock
    private ImageUrlConverter imageUrlConverter;

    @Mock
    private UserClient userClient;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private PostDetailCacheService postDetailCacheService;

    @Test
    @DisplayName("게시글 상세 조회")
    void getPostDetail() {
        // Given
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Post post = Post.builder().authorId(userId).content("내용").build();
        ApiResponse<UserResponse> authorResponse = ApiResponse.success(new UserResponse(userId, "닉네임"));

        when(postReader.findById(postId)).thenReturn(post);
        when(imageService.findAllByPostId(postId)).thenReturn(List.of());
        when(userClient.getUser(userId)).thenReturn(authorResponse);
        when(imageUrlConverter.toImageUrls(anyList())).thenReturn(List.of());

        // When
        PostCache.Detail result = postDetailCacheService.getPostDetail(postId);

        // Then
        assertThat(result.postId()).isEqualTo(post.getId());
        verify(postReader).findById(postId);
        verify(imageService).findAllByPostId(postId);
        verify(userClient).getUser(userId);
        verify(imageUrlConverter).toImageUrls(anyList());
    }

    @Nested
    @DisplayName("게시글 상세 배치 조회")
    class GetPostDetailBatch {
        @Test
        @DisplayName("전체 캐시 히트 - DB 조회 없음")
        void getPostDetailBatchAllCacheHit() {
            // Given
            UUID id = UUID.randomUUID();
            PostCache.Detail cached = mock(PostCache.Detail.class);

            when(cacheManager.getCache(RedisKeyPrefix.POST_DETAIL)).thenReturn(cache);
            when(cache.get(id, PostCache.Detail.class)).thenReturn(cached);

            // When
            List<PostCache.Detail> results = postDetailCacheService.getPostDetailBatch(List.of(id));

            // Then
            assertThat(results).containsExactly(cached);
            verifyNoInteractions(postReader, imageService, userClient);
        }

        @Test
        @DisplayName("전체 캐시 미스 - 배치 조회 후 캐시 저장")
        void getPostDetailBatchAllCacheMiss() {
            // Given
            UUID id = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            Post post = mock(Post.class);
            UserResponse author = mock(UserResponse.class);
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(cacheManager.getCache(RedisKeyPrefix.POST_DETAIL)).thenReturn(cache);
            when(cache.get(id, PostCache.Detail.class)).thenReturn(null);
            when(postReader.findAllByIds(List.of(id))).thenReturn(List.of(post));
            when(post.getId()).thenReturn(id);
            when(post.getAuthorId()).thenReturn(authorId);
            when(imageService.findAllByPostIds(List.of(id))).thenReturn(Map.of());
            when(userClient.getUsers(Set.of(authorId))).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of(author));
            when(author.userId()).thenReturn(authorId);

            // When
            postDetailCacheService.getPostDetailBatch(List.of(id));

            // Then
            verify(postReader).findAllByIds(List.of(id));
            verify(userClient).getUsers(Set.of(authorId));
            verify(cache).put(eq(id), any(PostCache.Detail.class));
        }

        @Test
        @DisplayName("캐시 히트/미스 혼합 - 미스된 것만 배치 조회")
        void getPostDetailBatchMixed() {
            // Given
            UUID hitId = UUID.randomUUID();
            UUID missId = UUID.randomUUID();
            PostCache.Detail cached = mock(PostCache.Detail.class);
            Post post = mock(Post.class);
            UUID authorId = UUID.randomUUID();
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(cacheManager.getCache(RedisKeyPrefix.POST_DETAIL)).thenReturn(cache);
            when(cache.get(hitId, PostCache.Detail.class)).thenReturn(cached);
            when(cache.get(missId, PostCache.Detail.class)).thenReturn(null);
            when(postReader.findAllByIds(List.of(missId))).thenReturn(List.of(post));
            when(post.getId()).thenReturn(missId);
            when(post.getAuthorId()).thenReturn(authorId);
            when(imageService.findAllByPostIds(List.of(missId))).thenReturn(Map.of());
            when(userClient.getUsers(Set.of(authorId))).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());

            // When
            postDetailCacheService.getPostDetailBatch(List.of(hitId, missId));

            // Then
            verify(postReader).findAllByIds(List.of(missId));
        }
    }
}