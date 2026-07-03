package com.fandom.feed.application;

import com.fandom.feed.domain.entity.Post;
import com.fandom.feed.domain.repository.PostRepository;
import com.fandom.feed.global.constant.FeedPolicy;
import com.fandom.feed.infra.client.UserClientRetryWrapper;
import com.fandom.feed.infra.client.dto.FollowingResponse;
import com.fandom.feed.infra.redis.LargeFollowingCacheService;
import com.fandom.feed.infra.redis.PostListCacheService;
import com.fandom.feed.infra.redis.TimelineCacheService;
import com.fandom.feed.infra.util.UuidV7Generator;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {
    @InjectMocks
    private TimelineService timelineService;

    @Mock
    private PostAssembler postAssembler;

    @Mock
    private PostRepository postRepository;

    @Mock
    private TimelineCacheService timelineCacheService;

    @Mock
    private LargeFollowingCacheService largeFollowingCacheService;

    @Mock
    private PostListCacheService postListCacheService;

    @Mock
    private UserClientRetryWrapper userClient;

    private final UuidV7Generator uuidV7Generator = new UuidV7Generator();
    private final UUID userId = UUID.randomUUID();

    private UUID nextId() throws InterruptedException {
        UUID id = uuidV7Generator.generate();
        Thread.sleep(2);
        return id;
    }

    @Nested
    @DisplayName("타임라인 조회 분기")
    class GetTimeline {
        @Test
        @DisplayName("타임라인 캐시가 있으면 캐시 히트 경로로 처리")
        void getTimelineWhenCacheExists() {
            // given
            given(timelineCacheService.exists(userId)).willReturn(true);
            given(timelineCacheService.getPostIds(userId, null)).willReturn(List.of());
            given(largeFollowingCacheService.exists(userId)).willReturn(true);
            given(largeFollowingCacheService.getLargeFollowingIds(userId)).willReturn(List.of());
            given(postAssembler.buildCacheResponse(anyList(), eq(userId)))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));

            // when
            timelineService.getTimeline(userId, null);

            // then
            verify(postAssembler).buildCacheResponse(anyList(), eq(userId));
            verify(userClient, never()).getFollowingIds(any(), any());
        }

        @Test
        @DisplayName("타임라인 캐시가 없으면 캐시 미스 경로로 처리")
        void getTimelineWhenCacheMissing() {
            // given
            given(timelineCacheService.exists(userId)).willReturn(false);
            given(userClient.getFollowingIds(eq(userId), any()))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));
            given(postRepository.findByAuthorIdInForWarm(anyList())).willReturn(List.of());
            given(postAssembler.buildDBResponse(anyList(), any(), anyBoolean(), eq(userId), eq(false)))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));

            // when
            timelineService.getTimeline(userId, null);

            // then
            verify(postAssembler).buildDBResponse(anyList(), any(), anyBoolean(), eq(userId), eq(false));
            verify(timelineCacheService, never()).getPostIds(any(), any());
        }
    }

    @Nested
    @DisplayName("캐시 히트 시 타임라인 조회")
    class GetFromCacheHit {
        @Test
        @DisplayName("일반 크리에이터와 대형 크리에이터 게시글을 최신순으로 병합")
        void mergesSmallAndLargeAuthorPosts() throws InterruptedException {
            // given
            UUID smallPostId = nextId();
            UUID largePostId = nextId();

            given(timelineCacheService.exists(userId)).willReturn(true);
            given(timelineCacheService.getPostIds(userId, null)).willReturn(List.of(smallPostId));

            given(largeFollowingCacheService.exists(userId)).willReturn(true);
            UUID largeAuthorId = UUID.randomUUID();
            given(largeFollowingCacheService.getLargeFollowingIds(userId)).willReturn(List.of(largeAuthorId));

            given(postListCacheService.getPostIdsBatch(List.of(largeAuthorId), null))
                    .willReturn(Map.of(largeAuthorId, List.of(largePostId)));

            given(postAssembler.buildCacheResponse(anyList(), eq(userId)))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));

            // when
            timelineService.getTimeline(userId, null);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
            verify(postAssembler).buildCacheResponse(captor.capture(), eq(userId));
            assertThat(captor.getValue()).containsExactly(largePostId, smallPostId);
        }

        @Test
        @DisplayName("타임라인 캐시 postId 목록이 null이어도 대형 크리에이터 게시글 조회 가능")
        void survivesWhenSmallAuthorPostIdsIsNull() {
            // given
            UUID largeAuthorId = UUID.randomUUID();
            UUID largePostId = UUID.randomUUID();

            given(timelineCacheService.exists(userId)).willReturn(true);
            given(timelineCacheService.getPostIds(userId, null)).willReturn(null);

            given(largeFollowingCacheService.exists(userId)).willReturn(true);
            given(largeFollowingCacheService.getLargeFollowingIds(userId)).willReturn(List.of(largeAuthorId));
            given(postListCacheService.getPostIdsBatch(List.of(largeAuthorId), null))
                    .willReturn(Map.of(largeAuthorId, List.of(largePostId)));

            given(postAssembler.buildCacheResponse(anyList(), eq(userId)))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));

            // when
            timelineService.getTimeline(userId, null);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
            verify(postAssembler).buildCacheResponse(captor.capture(), eq(userId));
            assertThat(captor.getValue()).containsExactly(largePostId);
        }

        @Test
        @DisplayName("대형 크리에이터 팔로잉 캐시가 없으면 User 서비스에서 조회 후 캐시에 저장")
        void resolvesLargeFollowingIdsFromUserServiceWhenCacheMissing() {
            // given
            UUID largeAuthorId = UUID.randomUUID();

            given(timelineCacheService.exists(userId)).willReturn(true);
            given(timelineCacheService.getPostIds(userId, null)).willReturn(List.of());

            given(largeFollowingCacheService.exists(userId)).willReturn(false);
            given(userClient.getLargeFollowingIds(eq(userId), any()))
                    .willReturn(CursorPageResponse.of(List.of(largeAuthorId), null, false));
            given(postListCacheService.getPostIdsBatch(List.of(largeAuthorId), null)).willReturn(Map.of());

            given(postAssembler.buildCacheResponse(anyList(), eq(userId)))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));

            // when
            timelineService.getTimeline(userId, null);

            // then
            verify(largeFollowingCacheService).addLargeFollowing(userId, List.of(largeAuthorId));
        }

        @Test
        @DisplayName("대형 크리에이터 게시글이 캐시 미스면 DB에서 postId 조회")
        void fetchesFromDbWhenLargeAuthorPostCacheMissing() {
            // given
            UUID largeAuthorId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();

            given(timelineCacheService.exists(userId)).willReturn(true);
            given(timelineCacheService.getPostIds(userId, null)).willReturn(List.of());

            given(largeFollowingCacheService.exists(userId)).willReturn(true);
            given(largeFollowingCacheService.getLargeFollowingIds(userId)).willReturn(List.of(largeAuthorId));

            Map<UUID, List<UUID>> batchResult = new HashMap<>();
            batchResult.put(largeAuthorId, null);
            given(postListCacheService.getPostIdsBatch(List.of(largeAuthorId), null)).willReturn(batchResult);
            given(postRepository.findIdsByCursorAndAuthorIdIn(null, List.of(largeAuthorId)))
                    .willReturn(List.of(postId));

            given(postAssembler.buildCacheResponse(anyList(), eq(userId)))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));

            // when
            timelineService.getTimeline(userId, null);

            // then
            verify(postRepository).findIdsByCursorAndAuthorIdIn(null, List.of(largeAuthorId));
        }
    }

    @Nested
    @DisplayName("캐시 미스 시 타임라인 워밍업")
    class GetFromCacheMiss {
        @Test
        @DisplayName("팔로잉을 일반/대형 크리에이터로 분류하여 각각 처리한다")
        void classifiesFollowingsAndProcessesSeparately() {
            // given
            UUID smallAuthorId = UUID.randomUUID();
            UUID largeAuthorId = UUID.randomUUID();

            given(timelineCacheService.exists(userId)).willReturn(false);
            given(userClient.getFollowingIds(eq(userId), any())).willReturn(
                    CursorPageResponse.of(
                            List.of(new FollowingResponse(smallAuthorId, false),
                                    new FollowingResponse(largeAuthorId, true)),
                            null, false
                    )
            );

            given(postRepository.findByAuthorIdInForWarm(List.of(smallAuthorId))).willReturn(List.of());
            given(postListCacheService.getPostIdsBatch(List.of(largeAuthorId), null)).willReturn(Map.of());

            given(postAssembler.buildDBResponse(anyList(), any(), anyBoolean(), eq(userId), eq(false)))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));

            // when
            timelineService.getTimeline(userId, null);

            // then
            verify(postRepository).findByAuthorIdInForWarm(List.of(smallAuthorId));
            verify(largeFollowingCacheService).addLargeFollowing(userId, List.of(largeAuthorId));
        }

        @Test
        @DisplayName("일반 크리에이터 게시글을 타임라인 캐시에 워밍업")
        void warmsUpTimelineCacheWithSmallAuthorPosts() {
            // given
            UUID smallAuthorId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();
            Post post = mock(Post.class);
            given(post.getId()).willReturn(postId);

            given(timelineCacheService.exists(userId)).willReturn(false);
            given(userClient.getFollowingIds(eq(userId), any())).willReturn(
                    CursorPageResponse.of(List.of(new FollowingResponse(smallAuthorId, false)), null, false)
            );
            given(postRepository.findByAuthorIdInForWarm(List.of(smallAuthorId))).willReturn(List.of(post));
            given(postAssembler.buildDBResponse(anyList(), any(), anyBoolean(), eq(userId), eq(false)))
                    .willReturn(CursorPageResponse.of(List.of(), null, false));

            // when
            timelineService.getTimeline(userId, null);

            // then
            verify(timelineCacheService).addPostsForWarm(userId, List.of(postId));
        }

        @Test
        @DisplayName("병합 결과가 PAGE_SIZE를 초과하면 hasNext가 true이고 다음 커서가 설정")
        void setsHasNextWhenMergedExceedsPageSize() throws InterruptedException {
            // given
            UUID smallAuthorId = UUID.randomUUID();
            List<Post> posts = new ArrayList<>();
            for (int i = 0; i <= FeedPolicy.PAGE_SIZE; i++) {
                UUID postId = nextId();
                Post post = mock(Post.class);
                given(post.getId()).willReturn(postId);
                posts.add(post);
            }
            Collections.reverse(posts);

            given(timelineCacheService.exists(userId)).willReturn(false);
            given(userClient.getFollowingIds(eq(userId), any())).willReturn(
                    CursorPageResponse.of(List.of(new FollowingResponse(smallAuthorId, false)), null, false)
            );
            given(postRepository.findByAuthorIdInForWarm(List.of(smallAuthorId))).willReturn(posts);
            given(postAssembler.buildDBResponse(anyList(), any(), eq(true), eq(userId), eq(false)))
                    .willReturn(CursorPageResponse.of(List.of(), null, true));

            // when
            timelineService.getTimeline(userId, null);

            // then
            ArgumentCaptor<Boolean> hasNextCaptor = ArgumentCaptor.forClass(Boolean.class);
            verify(postAssembler).buildDBResponse(anyList(), any(), hasNextCaptor.capture(), eq(userId), eq(false));
            assertThat(hasNextCaptor.getValue()).isTrue();
        }
    }
}