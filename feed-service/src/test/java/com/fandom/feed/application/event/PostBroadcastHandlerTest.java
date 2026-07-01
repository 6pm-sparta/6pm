package com.fandom.feed.application.event;

import com.fandom.feed.application.FanoutService;
import com.fandom.feed.infra.client.UserClientRetryWrapper;
import com.fandom.feed.infra.kafka.NotificationPublisher;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostBroadcastHandlerTest {
    @Mock
    private UserClientRetryWrapper userClientRetryWrapper;

    @Mock
    private FanoutService fanoutService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private PostBroadcastHandler handler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "fanoutThreshold", 10000L);
        ReflectionTestUtils.setField(handler, "chunkSize", 500);
    }

    @Nested
    @DisplayName("게시글 생성 이벤트 발생")
    class HandlePostCreated {
        @Test
        @DisplayName("팔로워 1만 명 이하 - 팬아웃과 알람 모두 발생")
        void handlePostCreatedWithinFanoutThreshold() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(userClientRetryWrapper.countFollowers(authorId)).willReturn(5000L);
            given(userClientRetryWrapper.getFollowerIds(eq(authorId), isNull(), anyInt()))
                    .willReturn(CursorPageResponse.of(List.of(userId), null, false));

            // when
            handler.handlePostCreated(postId, authorId, "닉네임");

            // then
            verify(fanoutService).insertChunk(eq(postId), isNull(), eq(List.of(userId)));
            verify(notificationPublisher).publishChunk(eq(postId), eq("닉네임"), isNull(), eq(List.of(userId)));
        }

        @Test
        @DisplayName("팔로워 1만 명 초과 - 알람만 발생")
        void handlePostCreatedExceedsFanoutThreshold() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(userClientRetryWrapper.countFollowers(authorId)).willReturn(10_001L);
            given(userClientRetryWrapper.getFollowerIds(eq(authorId), isNull(), anyInt()))
                    .willReturn(CursorPageResponse.of(List.of(userId), null, false));

            // when
            handler.handlePostCreated(postId, authorId, "닉네임");

            // then
            verify(fanoutService, never()).insertChunk(any(), any(), any());
            verify(notificationPublisher).publishChunk(eq(postId), eq("닉네임"), isNull(), eq(List.of(userId)));
        }

        @Test
        @DisplayName("팔로워 페이지 여러 개 - cursor가 갱신되며 모든 페이지 순회")
        void handlePostCreatedMultiplePages() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            UUID firstUser = UUID.randomUUID();
            UUID secondUser = UUID.randomUUID();
            UUID midCursor = UUID.randomUUID();

            given(userClientRetryWrapper.countFollowers(authorId)).willReturn(100L);
            given(userClientRetryWrapper.getFollowerIds(eq(authorId), isNull(), anyInt()))
                    .willReturn(CursorPageResponse.of(List.of(firstUser), midCursor, true));
            given(userClientRetryWrapper.getFollowerIds(eq(authorId), eq(midCursor), anyInt()))
                    .willReturn(CursorPageResponse.of(List.of(secondUser), null, false));

            // when
            handler.handlePostCreated(postId, authorId, "닉네임");

            // then
            verify(userClientRetryWrapper, times(2)).getFollowerIds(eq(authorId), any(), anyInt());
            verify(fanoutService).insertChunk(postId, null, List.of(firstUser));
            verify(fanoutService).insertChunk(postId, midCursor, List.of(secondUser));
        }

        @Test
        @DisplayName("팔로워 없음 - 팔로워 목록 조회 없이 즉시 종료")
        void handlePostCreatedNoFollowers() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();

            given(userClientRetryWrapper.countFollowers(authorId)).willReturn(0L);

            // when
            handler.handlePostCreated(postId, authorId, "닉네임");

            // then
            verify(userClientRetryWrapper, never()).getFollowerIds(any(), any(), anyInt());
            verify(fanoutService, never()).insertChunk(any(), any(), any());
            verify(notificationPublisher, never()).publishChunk(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("게시글 삭제 이벤트 발생")
    class HandlePostDeleted {
        @Test
        @DisplayName("팔로워 1만 명 이하 - 팬아웃 발생")
        void handlePostDeletedWithinFanoutThreshold() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            given(userClientRetryWrapper.countFollowers(authorId)).willReturn(5000L);
            given(userClientRetryWrapper.getFollowerIds(eq(authorId), isNull(), anyInt()))
                    .willReturn(CursorPageResponse.of(List.of(userId), null, false));

            // when
            handler.handlePostDeleted(postId, authorId);

            // then
            verify(fanoutService).removeChunk(eq(postId), isNull(), eq(List.of(userId)));
        }

        @Test
        @DisplayName("팔로워 1만 명 초과 - 아무것도 하지 않음")
        void handlePostDeletedExceedsFanoutThreshold() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();

            given(userClientRetryWrapper.countFollowers(authorId)).willReturn(10_001L);

            // when
            handler.handlePostDeleted(postId, authorId);

            // then
            verify(userClientRetryWrapper, never()).getFollowerIds(any(), any(), anyInt());
            verify(fanoutService, never()).removeChunk(any(), any(), any());
        }

        @Test
        @DisplayName("팔로워 페이지 여러 개 - cursor가 갱신되며 모든 페이지 순회")
        void handlePostDeletedMultiplePages() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            UUID firstUser = UUID.randomUUID();
            UUID secondUser = UUID.randomUUID();
            UUID midCursor = UUID.randomUUID();

            given(userClientRetryWrapper.countFollowers(authorId)).willReturn(100L);
            given(userClientRetryWrapper.getFollowerIds(eq(authorId), isNull(), anyInt()))
                    .willReturn(CursorPageResponse.of(List.of(firstUser), midCursor, true));
            given(userClientRetryWrapper.getFollowerIds(eq(authorId), eq(midCursor), anyInt()))
                    .willReturn(CursorPageResponse.of(List.of(secondUser), null, false));

            // when
            handler.handlePostDeleted(postId, authorId);

            // then
            verify(fanoutService).removeChunk(postId, null, List.of(firstUser));
            verify(fanoutService).removeChunk(postId, midCursor, List.of(secondUser));
        }

        @Test
        @DisplayName("팔로워 없음 - 팔로워 목록 조회 없이 즉시 종료")
        void handleDeletedNoFollowers() {
            // given
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();

            given(userClientRetryWrapper.countFollowers(authorId)).willReturn(0L);

            // when
            handler.handlePostDeleted(postId, authorId);

            // then
            verify(userClientRetryWrapper, never()).getFollowerIds(any(), any(), anyInt());
            verify(fanoutService, never()).removeChunk(any(), any(), any());
        }
    }
}