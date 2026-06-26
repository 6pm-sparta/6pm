package com.fandom.feed.application;

import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.client.dto.UserResponse;
import com.fandom.feed.infra.redis.PostDetailCacheService;
import com.fandom.feed.infra.redis.ReactionCacheService;
import com.fandom.feed.infra.redis.dto.PostCache;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import com.fandom.feed.presentation.dto.response.PostResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PostAssemblerTest {
    @Mock
    private ImageService imageService;

    @Mock
    private PostDetailCacheService postDetailCacheService;

    @Mock
    private ReactionCacheService reactionCacheService;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private PostAssembler postAssembler;

    @Nested
    @DisplayName("캐시에서 가져온 postId 목록으로 응답 구성")
    class BuildCacheResponse {
        @Test
        @DisplayName("PAGE_SIZE 이하 - hasMore = false, nextCursor = null")
        void noHasMoreWhenUnderPageSize() {
            // given
            UUID postId = UUID.randomUUID();

            when(postDetailCacheService.getPostDetailBatch(any())).thenReturn(List.of(mock(PostCache.Detail.class)));
            when(reactionCacheService.getReactionInfoBatch(any(), any(), anyBoolean()))
                    .thenReturn(List.of(mock(PostCache.ReactionInfo.class)));

            // when
            CursorPageResponse<PostResponse.Summary> result = postAssembler.buildCacheResponse(List.of(postId), null);

            // then
            assertThat(result.hasMore()).isFalse();
            assertThat(result.nextCursor()).isNull();
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("PAGE_SIZE 초과 - hasMore = true, nextCursor = 마지막 postId")
        void hasMoreWhenExceedsPageSize() {
            // given
            List<UUID> postIds = IntStream.range(0, FeedPolicy.PAGE_SIZE + 1).mapToObj(i -> UUID.randomUUID()).toList();

            when(postDetailCacheService.getPostDetailBatch(any())).thenReturn(IntStream.range(0, FeedPolicy.PAGE_SIZE)
                    .mapToObj(i -> mock(PostCache.Detail.class)).toList());
            when(reactionCacheService.getReactionInfoBatch(any(), any(), anyBoolean()))
                    .thenReturn(IntStream.range(0, FeedPolicy.PAGE_SIZE)
                            .mapToObj(i -> mock(PostCache.ReactionInfo.class)).toList());

            // when
            CursorPageResponse<PostResponse.Summary> result = postAssembler.buildCacheResponse(postIds, null);

            // then
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextCursor()).isEqualTo(postIds.get(FeedPolicy.PAGE_SIZE - 1));
            assertThat(result.content()).hasSize(FeedPolicy.PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("Post 엔티티로 응답 구성")
    class BuildDBResponse {
        @Test
        @DisplayName("image, author, reaction 배치 조회 각 1번씩")
        void buildDBResponse() {
            // given
            List<Post> posts = List.of(mockPost(), mockPost(), mockPost());
            List<UUID> postIds = posts.stream().map(Post::getId).toList();
            ApiResponse<List<UserResponse>> apiResponse = mock();

            when(imageService.findAllByPostIds(postIds)).thenReturn(Map.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());
            when(reactionCacheService.getReactionInfoBatch(postIds, null, false))
                    .thenReturn(List.of(
                            mock(PostCache.ReactionInfo.class),
                            mock(PostCache.ReactionInfo.class),
                            mock(PostCache.ReactionInfo.class)
                    ));

            // when
            postAssembler.buildDBResponse(posts, null, false, null, false);

            // then
            verify(imageService, times(1)).findAllByPostIds(any());
            verify(userClient, times(1)).getUsers(any());
            verify(reactionCacheService, times(1)).getReactionInfoBatch(any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("PAGE_SIZE 초과 - hasMore = true, nextCursor 설정")
        void buildDBResponseWhenExceedsPageSize() {
            // given
            List<Post> posts = IntStream.range(0, FeedPolicy.PAGE_SIZE).mapToObj(i -> mockPost()).toList();
            ApiResponse<List<UserResponse>> apiResponse = mock();
            UUID nextCursor = UUID.randomUUID();

            when(imageService.findAllByPostIds(any())).thenReturn(Map.of());
            when(userClient.getUsers(any())).thenReturn(apiResponse);
            when(apiResponse.getData()).thenReturn(List.of());
            when(reactionCacheService.getReactionInfoBatch(any(), any(), anyBoolean()))
                    .thenReturn(IntStream.range(0, FeedPolicy.PAGE_SIZE)
                            .mapToObj(i -> mock(PostCache.ReactionInfo.class)).toList());

            // when
            CursorPageResponse<PostResponse.Summary> result = postAssembler.buildDBResponse(
                    posts, nextCursor, true, null, false
            );

            // then
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextCursor()).isNotNull();
            assertThat(result.content()).hasSize(FeedPolicy.PAGE_SIZE);
        }
    }

    private Post mockPost() {
        Post post = mock(Post.class);
        lenient().when(post.getId()).thenReturn(UUID.randomUUID());
        lenient().when(post.getAuthorId()).thenReturn(UUID.randomUUID());
        return post;
    }
}