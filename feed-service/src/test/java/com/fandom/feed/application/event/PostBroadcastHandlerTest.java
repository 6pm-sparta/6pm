package com.fandom.feed.application.event;

import com.fandom.common.dto.ApiResponse;
import com.fandom.feed.application.FanoutService;
import com.fandom.feed.global.constant.BroadcastPolicy;
import com.fandom.feed.infra.client.UserClient;
import com.fandom.feed.infra.kafka.NotificationPublisher;
import com.fandom.feed.presentation.dto.response.CursorPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostBroadcastHandlerTest {
    @Mock
    UserClient userClient;

    @Mock
    FanoutService fanoutService;

    @Mock
    NotificationPublisher notificationPublisher;

    @InjectMocks
    PostBroadcastHandler handler;

    @Test
    @DisplayName("팔로워 1만 명 이하 - 팬아웃과 알람 모두 발생")
    void handleWithinFanoutThreshold() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(userClient.countFollowers(authorId)).willReturn(ApiResponse.success(5000L));
        given(userClient.getFollowerIds(eq(authorId), isNull(), eq(BroadcastPolicy.CHUNK_SIZE)))
                .willReturn(ApiResponse.success(CursorPageResponse.of(List.of(userId), null, false)));

        handler.handle(postId, authorId, "닉네임");

        verify(fanoutService).insertChunk(eq(postId), isNull(), eq(List.of(userId)));
        verify(notificationPublisher).publishChunk(eq(postId), eq("닉네임"), isNull(), eq(List.of(userId)));
    }

    @Test
    @DisplayName("팔로워 1만 명 초과 - 알람만 발생")
    void handleExceedsFanoutThreshold() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(userClient.countFollowers(authorId)).willReturn(ApiResponse.success(10_001L));
        given(userClient.getFollowerIds(eq(authorId), isNull(), eq(BroadcastPolicy.CHUNK_SIZE)))
                .willReturn(ApiResponse.success(CursorPageResponse.of(List.of(userId), null, false)));

        handler.handle(postId, authorId, "닉네임");

        verify(fanoutService, never()).insertChunk(any(), any(), any());
        verify(notificationPublisher).publishChunk(eq(postId), eq("닉네임"), isNull(), eq(List.of(userId)));
    }

    @Test
    @DisplayName("팔로워 페이지 여러 개 - cursor가 갱신되며 모든 페이지 순회")
    void handleMultiplePages() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        UUID midCursor = UUID.randomUUID();

        given(userClient.countFollowers(authorId)).willReturn(ApiResponse.success(100L));
        given(userClient.getFollowerIds(eq(authorId), isNull(), eq(BroadcastPolicy.CHUNK_SIZE)))
                .willReturn(ApiResponse.success(CursorPageResponse.of(List.of(firstUser), midCursor, true)));
        given(userClient.getFollowerIds(eq(authorId), eq(midCursor), eq(BroadcastPolicy.CHUNK_SIZE)))
                .willReturn(ApiResponse.success(CursorPageResponse.of(List.of(secondUser), null, false)));

        handler.handle(postId, authorId, "닉네임");

        verify(userClient, times(2)).getFollowerIds(eq(authorId), any(), eq(BroadcastPolicy.CHUNK_SIZE));
        verify(fanoutService).insertChunk(postId, null, List.of(firstUser));
        verify(fanoutService).insertChunk(postId, midCursor, List.of(secondUser));
    }

    @Test
    @DisplayName("팔로워 없음 - 팬아웃과 알람 모두 미호출")
    void handleNoFollowers() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        given(userClient.countFollowers(authorId)).willReturn(ApiResponse.success(0L));
        given(userClient.getFollowerIds(eq(authorId), isNull(), eq(BroadcastPolicy.CHUNK_SIZE)))
                .willReturn(ApiResponse.success(CursorPageResponse.of(List.of(), null, false)));

        handler.handle(postId, authorId, "닉네임");

        verify(fanoutService, never()).insertChunk(any(), any(), any());
        verify(notificationPublisher, never()).publishChunk(any(), any(), any(), any());
    }
}